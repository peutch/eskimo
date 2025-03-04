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

set -e

echo " - Injecting topology"
. /usr/local/sbin/inContainerInjectTopology.sh

echo " - Inject settings"
/usr/local/sbin/settingsInjector.sh prometheus

# starting prometheus process
if [[ $MASTER_PROMETHEUS_1 == $SELF_IP_ADDRESS ]]; then

    echo " - Starting service prometheus"
    /usr/local/lib/prometheus/prometheus \
        --config.file=/usr/local/lib/prometheus/prometheus.yml \
        > /var/log/prometheus/prometheus.log 2>&1 &
    export PROMETHEUS_PROC_ID=$!

    echo " - Checking Prometheus startup"
    sleep 5
    if [[ `ps -e | grep $PROMETHEUS_PROC_ID` == "" ]]; then
        echo " !! Failed to start Prometheus !!"
        cat /var/log/prometheus/prometheus.log
        exit 8
    fi
else
    echo " - (Not Starting service prometheus since master is $MASTER_PROMETHEUS_1 and I am $SELF_IP_ADDRESS)" \
        | tee /var/log/prometheus/prometheus.log
fi


# Starting node exporter process
echo " - Starting node exporter"
/usr/local/lib/prometheus/exporters/node_exporter/node_exporter \
        --path.procfs /host/proc \
        --path.sysfs /host/sys \
        --collector.filesystem.ignored-mount-points "^/(sys|proc|dev|host|etc)($|/)"\
    > /var/log/prometheus/node-exporter.log 2>&1 &
export NODE_EXPORTER_PROC_ID=$!

echo " - Checking Node exporter startup"
sleep 5
if [[ `ps -e | grep $NODE_EXPORTER_PROC_ID` == "" ]]; then
    echo " !! Failed to start Node exporter !!"
    cat /var/log/prometheus/node-exporter.log
    exit 9
fi

# running watch dogs

if [[ $MASTER_PROMETHEUS_1 == $SELF_IP_ADDRESS ]]; then

    echo " - Launching Watch Dog on Node exporter"
    /usr/local/sbin/containerWatchDog.sh $PROMETHEUS_PROC_ID $NODE_EXPORTER_PROC_ID /var/log/prometheus/node-exporter-watchdog.log &
fi

# TODO launch other watchdogs as appropriate

echo " - Now waiting on main process to exit"
wait $NODE_EXPORTER_PROC_ID


