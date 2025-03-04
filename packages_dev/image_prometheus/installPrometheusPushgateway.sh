#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
# Author : eskimo.sh / https://www.eskimo.sh
#
# Eskimo is available under a dual licensing model : commercial and GNU AGPL.
# If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
# terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
# Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
# any later version.
# Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
# commercial license.
#
# Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Affero Public License for more details.
#
# You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
# see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
# Boston, MA, 02110-1301 USA.
#
# You can be released from the requirements of the license by purchasing a commercial license. Buying such a
# commercial license is mandatory as soon as :
# - you develop activities involving Eskimo without disclosing the source code of your own product, software,
#   platform, use cases or scripts.
# - you deploy eskimo as part of a commercial product, platform or software.
# For more information, please contact eskimo.sh at https://www.eskimo.sh
#
# The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
# Software.
#

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- INSTALLING PROMETHEUS PUSHGATEWAY ---------------------------------------"

if [ -z "$PROMETHEUS_VERSION" ]; then
    echo "Need to set PROMETHEUS_VERSION environment variable before calling this script !"
    exit 1
fi

if [ -z "$PROMETHEUS_PUSHGATEWAY_VERSION" ]; then
    echo "Need to set PROMETHEUS_PUSHGATEWAY_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT
trap returned_to_saved_dir ERR

echo " - Changing to temp directory"
rm -Rf /tmp/prometheus_pe_setup
mkdir -p /tmp/prometheus_pe_setup
cd /tmp/prometheus_pe_setup

echo " - Downloading pushgateway-$PROMETHEUS_PUSHGATEWAY_VERSION"
wget https://github.com/prometheus/pushgateway/releases/download/v$PROMETHEUS_PUSHGATEWAY_VERSION/pushgateway-$PROMETHEUS_PUSHGATEWAY_VERSION.linux-amd64.tar.gz \
        > /tmp/prometheus_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad pushgateway-$PROMETHEUS_PUSHGATEWAY_VERSION from github. Trying to download from niceideas.ch"
    exit 1
    #wget http://niceideas.ch/mes/prometheus-$PROMETHEUS_VERSION.tar.gz >> /tmp/prometheus_install_log 2>&1
    #fail_if_error $? "/tmp/prometheus_install_log" -1
fi


echo " - Extracting pushgateway-$PROMETHEUS_VERSION"
tar -xvf pushgateway-$PROMETHEUS_PUSHGATEWAY_VERSION.linux-amd64.tar.gz > /tmp/prometheus_install_log 2>&1
fail_if_error $? "/tmp/prometheus_install_log" -2

echo " - Installing process exporter"
sudo chown -R root.staff pushgateway-$PROMETHEUS_PUSHGATEWAY_VERSION.linux-amd64/
sudo mkdir -p /usr/local/lib/prometheus-$PROMETHEUS_VERSION.linux-amd64/exporters/
sudo mv pushgateway-$PROMETHEUS_PUSHGATEWAY_VERSION.linux-amd64 /usr/local/lib/prometheus-$PROMETHEUS_VERSION.linux-amd64/exporters/

#sudo mkdir -p /usr/local/lib/prometheus_$SCALA_VERSION-$PROMETHEUS_VERSION/data
#sudo chmod -R 777 /usr/local/lib/prometheus_$SCALA_VERSION-$PROMETHEUS_VERSION/data
#sudo mkdir -p /usr/local/lib/prometheus_$SCALA_VERSION-$PROMETHEUS_VERSION/logs
#sudo chmod -R 777 /usr/local/lib/prometheus_$SCALA_VERSION-$PROMETHEUS_VERSION/logs
#sudo chmod -R 777 /usr/local/lib/prometheus_$SCALA_VERSION-$PROMETHEUS_VERSION/config/

echo " - symlinking pushgateway to /usr/local/lib/prometheus/exporters/pushgateway"
sudo ln -s /usr/local/lib/prometheus-$PROMETHEUS_VERSION.linux-amd64/exporters/pushgateway-$PROMETHEUS_PUSHGATEWAY_VERSION.linux-amd64 \
        /usr/local/lib/prometheus/exporters/pushgateway

echo " - Registering test cleaning traps"
export ES_PROC_ID=-1
export PUSHGATEWAY_PROC_ID=-1
function check_stop_pushgateway(){
    if [[ $ES_PROC_ID != -1 ]]; then
        echo " - Stopping ES !!"
        kill -15 $ES_PROC_ID
    fi
    if [[ $PUSHGATEWAY_PROC_ID != -1 ]]; then
        echo " - Stopping Prometheus Pushgateway !!"
        kill -15 $PUSHGATEWAY_PROC_ID
    fi
}
trap check_stop_pushgateway 15
trap check_stop_pushgateway EXIT
trap check_stop_pushgateway ERR


echo " - Starting Prometheus Pushgateway"
/usr/local/lib/prometheus/exporters/pushgateway/pushgateway > /tmp/prometheus_run_log 2>&1 &
export PUSHGATEWAY_PROC_ID=$!

echo " - Checking Pushgateway startup"
sleep 10
if [[ `ps -e | grep $PUSHGATEWAY_PROC_ID` == "" ]]; then
    echo " !! Failed to start Pushgateway !!"
    cat /tmp/prometheus_run_log
    exit 8
fi

echo " - Stopping Pushgateway"
kill -15 $PUSHGATEWAY_PROC_ID
export PUSHGATEWAY_PROC_ID=-1


echo " - Cleaning build directory"
rm -Rf /tmp/prometheus_pe_setup
returned_to_saved_dir


# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"