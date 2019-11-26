#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

. $SCRIPT_DIR/commonBuild.sh "$@"

check_for_internet

check_for_vagrant

if [[ $USE_VIRTUALBOX == 1 ]]; then
    check_for_virtualbox
fi


mkdir -p /tmp/build-mesos-debian
saved_dir=`pwd`
cleanup() {
    #rm -f Vagrantfile
    #rm -f ssh_key
    #rm -f ssh_key.pub
    cd $saved_dir
}
trap cleanup 15
trap cleanup EXIT

err_report() {
    echo "Error on line $1"
    tail -n 2000 /tmp/build-mesos-debian-log
}

trap 'err_report $LINENO' ERR

set -e

cp common.sh /tmp/build-mesos-debian/common.sh
chmod 755 /tmp/build-mesos-debian/common.sh

cp buildMesosFromSources.sh /tmp/build-mesos-debian/buildMesosFromSources.sh
chmod 755 /tmp/build-mesos-debian/buildMesosFromSources.sh

rm -f /tmp/build-mesos-debian-log

cd /tmp/build-mesos-debian

create-vagrant-file

echo " - Destroying any previously existing VM"
vagrant destroy --force deb-build-node >> /tmp/build-mesos-debian-log 2>&1

echo " - Bringing Build VM up"
if [[ $USE_VIRTUALBOX == 1 ]]; then
    vagrant up deb-build-node >> /tmp/build-mesos-debian-log 2>&1
else
    vagrant up deb-build-node --provider=libvirt >> /tmp/build-mesos-debian-log 2>&1
fi

#deb http://httpredir.debian.org/debian/ jessie main
#deb http://httpredir.debian.org/debian/ jessie main contrib non-free

echo " - Updating the appliance"
vagrant ssh -c "sudo DEBIAN_FRONTEND=noninteractive apt-get -yq update" deb-build-node  >> /tmp/build-mesos-debian-log 2>&1

echo " - Upgrading the appliance"
vagrant ssh -c "sudo DEBIAN_FRONTEND=noninteractive apt-get -yq upgrade" deb-build-node  >> /tmp/build-mesos-debian-log 2>&1

echo " - Installing the latest OpenJDK"
vagrant ssh -c "sudo DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-7-jdk " deb-build-node  >> /tmp/build-mesos-debian-log 2>&1

echo " - Installing a few utility tools"
vagrant ssh -c "sudo DEBIAN_FRONTEND=noninteractive apt-get install -y tar wget git unzip curl moreutils" deb-build-node  >> /tmp/build-mesos-debian-log 2>&1

echo " - Installing build tools"
vagrant ssh -c "sudo DEBIAN_FRONTEND=noninteractive apt-get install -y build-essential autoconf libtool" deb-build-node  >> /tmp/build-mesos-debian-log 2>&1

echo " - Installing swapspace"
vagrant ssh -c "sudo DEBIAN_FRONTEND=noninteractive apt-get install -y swapspace" deb-build-node  >> /tmp/build-mesos-debian-log 2>&1

echo " - Installing python"
vagrant ssh -c "sudo DEBIAN_FRONTEND=noninteractive apt-get install -y python-dev python-six python-virtualenv python-pip" deb-build-node  >> /tmp/build-mesos-debian-log 2>&1

echo " - Installing other Mesos dependencies"
vagrant ssh -c "sudo DEBIAN_FRONTEND=noninteractive apt-get install -y libcurl4-nss-dev libsasl2-dev libsasl2-modules maven libapr1-dev libsvn-dev zlib1g-dev" deb-build-node  >> /tmp/build-mesos-debian-log 2>&1

echo " - Building Mesos"
vagrant ssh -c "sudo JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64/ /vagrant/buildMesosFromSources.sh" deb-build-node

echo " - Creating archive mesos-$AMESOS_VERSION.tar.gz"
vagrant ssh -c "sudo bash -c \"cd /usr/local/lib && tar cvfz mesos-$AMESOS_VERSION.tar.gz mesos-$AMESOS_VERSION\"" deb-build-node  >> /tmp/build-mesos-debian-log 2>&1

echo " - Copying Mesos archive to shared folder"
vagrant ssh -c "sudo mv /usr/local/lib/mesos-$AMESOS_VERSION.tar.gz /vagrant/" deb-build-node  >> /tmp/build-mesos-debian-log 2>&1

echo " - Dowloading mesos archive"
chmod 700 ssh_key
chmod 700 ssh_key.pub
scp -o StrictHostKeyChecking=no -i ssh_key vagrant@192.168.10.101:/vagrant/mesos-$AMESOS_VERSION.tar.gz . >> /tmp/build-mesos-debian-log 2>&1

if [[ -f "$SCRIPT_DIR/../../packages_distrib/eskimo_mesos-debian-$AMESOS_VERSION.tar.gz" ]]; then
    echo " - renaming previous eskimo_mesos-debian-$AMESOS_VERSION.tar.gz"
    mv $SCRIPT_DIR/../../packages_distrib/eskimo_mesos-debian-$AMESOS_VERSION.tar.gz $SCRIPT_DIR/../../packages_distrib/eskimo_mesos-debian-$AMESOS_VERSION.tar.gz.old
fi

echo " - Copying Mesos archive to distribution folder"
mv mesos-$AMESOS_VERSION.tar.gz $SCRIPT_DIR/../../packages_distrib/eskimo_mesos-debian_"$AMESOS_VERSION"_1.tar.gz

echo " - Destroying build VM"
vagrant destroy --force deb-build-node  >> /tmp/build-mesos-debian-log 2>&1
