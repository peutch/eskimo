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

# extract path arguments and create volume mount command part
export DOCKER_VOLUMES_ARGS=""

export PROCESS_NEXT="0"
for argument in "$@"; do
    if [[ $PROCESS_NEXT == "1" ]]; then
        if [[ -d $argument ]]; then
            export DIR=$argument
        else
            export DIR=`dirname $argument`
        fi
        if [[ `echo $DOCKER_VOLUMES_ARGS | grep "$DIR:$DIR:slave"` == "" ]]; then
            export DOCKER_VOLUMES_ARGS=" -v $DIR:$DIR:slave $DOCKER_VOLUMES_ARGS"
        fi
    fi
    if [[ $argument == "-j" || $argument == "--jar" || $argument == "-l" || $argument == "--library" || $argument == "-d" || $argument == "--defaults" || $argument == "-e" || $argument == "--environment" ]]; then
        export PROCESS_NEXT="1"
    else
        export PROCESS_NEXT="0"
    fi
done

# Add standard folders if not already part of it
if [[ `echo $DOCKER_VOLUMES_ARGS | grep /var/lib/flink` == "" ]]; then
    export DOCKER_VOLUMES_ARGS=" -v /var/lib/flink:/var/lib/flink:shared $DOCKER_VOLUMES_ARGS"
fi
if [[ `echo $DOCKER_VOLUMES_ARGS | grep /var/log/flink` == "" ]]; then
    export DOCKER_VOLUMES_ARGS=" -v /var/log/flink:/var/log/flink:shared $DOCKER_VOLUMES_ARGS"
fi

#echo $DOCKER_VOLUMES_ARGS


/usr/bin/docker run \
        -it \
        --rm \
        --network host \
        --user flink \
        --privileged \
        $DOCKER_VOLUMES_ARGS \
        --mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh \
        --mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json \
        --mount type=bind,source=/home/flink/.kube/config,target=/home/flink/.kube/config \
        --mount type=bind,source=/usr/local/bin/kubectl,target=/usr/local/bin/kubectl \
        -v /etc/k8s:/etc/k8s:ro \
        -e NODE_NAME=$HOSTNAME \
        kubernetes.registry:5000/flink \
        /usr/local/bin/sql-client.sh "$@"

# -p 5000-5010:5000-5010 \