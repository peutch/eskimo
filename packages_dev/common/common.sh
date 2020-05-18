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

# Version of software to install
export ESKIMO_VERSION=0.2
export DEBIAN_VERSION=debian_09_stretch

export FLINK_VERSION=1.10.0
export FLINK_HADOOP_VERSION=2.8.3-10.0

export HADOOP_MAJOR_VERSION=2.8

export SPARK_VERSION=2.4.5
export SPARK_VERSION_MAJOR=2.4

export AMESOS_VERSION=1.8.1

export MARATHON_VERSION_SHORT=1.8.222
export MARATHON_VERSION=1.8.222-86475ddac

export DOCKER_REGISTRY_VERSION=2.6.2~ds1-2+b21_amd64

export ES_VERSION=7.6.2
export ES_VERSION_MAJOR=7

export CEREBRO_VERSION=0.8.5

export KAFKA_VERSION=2.2.2
export KAFKA_MANAGER_VERSION=2.0.0.2

export SCALA_VERSION=2.11
export SPARK_STREAMING_KAFKA_CON_VERSION=0-10
export SPARK_STREAMING_KAFKA_CLIENT_VERSION=2.0.0
export SPARK_UNUSED_VERSION=1.0.0

export GDASH_VERSION=0.0.1

export GRAFANA_VERSION=6.7.3
export PROMETHEUS_VERSION=2.18.1
export PROMETHEUS_NODE_EXPORTER_VERSION=0.18.1
export PROMETHEUS_PUSHGATEWAY_VERSION=1.2.0
export PROMETHEUS_MESOS_EXPORTER_VERSION=1.1.2

# Zeppelin
export ZEPPELIN_VERSION_FULL=0.9.0-eskimo
export ZEPPELIN_VERSION=0.9.0
export ZEPPELIN_IS_SNAPSHOT="false" # set to "true" to build zeppelin from zeppelin git master


# This functions ensures that internet is available on host machine (the one running eskimo)
# Internet is indeed required to download source packages for services.
check_for_internet() {

    if [ -x "$(command -v wget)" ]; then
        wget https://www.google.com -O /tmp/test.html >/dev/null 2>&1
        if [[ $? != 0 ]]; then
            echo "No internet connection available"
            exit -10
        fi
    else
        echo "No wget command available"
        exit -11
    fi
}

# This function ensures that docker is available on host machine (the one running eskimo)
# Docker is required to build package images that will be installed on cluster nodes
check_for_docker() {
    if [ -x "$(command -v docker)" ]; then
        echo "Found docker : "`docker -v`
    else
        echo "Docker is not available on system"
        exit -1
    fi
}

# This function is used after proper building of a service image to close the image and save it
# Arguments are:
# - $1 the image name
# - $2 the log file to report problems to
function close_and_save_image() {

    if [[ $1 == "" ]]; then
        echo "Image needs to be passed in argument"
        exit -2
    fi

	if [[ $2 == "" ]]; then
        echo "Log file needs to be passed in argument"
        exit -2
    fi

    if [[ $3 == "" ]]; then
        echo "Software version needs to be passed in argument"
        exit -3
    fi
	
    echo " - Cleaning apt cache"
    docker exec -i $1 apt-get clean -q >> $2 2>&1
    fail_if_error $? $2 -2

    # Exit the container and commit the changes
    # Now that we've modified the container we have to commit the changes. First exit the container with the command exit.
    # To commit the changes and create a new image based on said changes, issue the command:
    echo " - Comitting changes from container $1 on image $1_template"
    docker commit $1 eskimo:$1_template >> $2 2>&1
    fail_if_error $? $2 -3

    # Stop container and delete image
    echo " - Stopping container $1"
    docker stop $1 >> $2 2>&1
    fail_if_error $? $2 -4

    echo " - removing container $1"
    docker container rm $1 >> $2 2>&1
    fail_if_error $? $2 -5

    # save base image
    echo " - Saving image $1_template"
	if [[ -z $TEST_MODE ]]; then set -e; fi
    docker save eskimo:$1_template | gzip >  ../../packages_distrib/tmp_image_$1_TEMP.tar.gz
    set +e

    echo " - versioning image"
    for i in `seq 1 100`; do
        if [[ ! -f "../../packages_distrib/docker_template_$1_$3_$i.tar.gz" ]]; then
            mv ../../packages_distrib/tmp_image_$1_TEMP.tar.gz ../../packages_distrib/docker_template_$1_$3_$i.tar.gz
            break;
        fi
    done

    #docker image rm `cat id_file`
    echo " - removing image $1_template"
    docker image rm eskimo:$1_template >> $2 2>&1
    fail_if_error $? $2 -6

}

# This function is to build the empty version of the docker container that will be used to build the image
# Arguments are:
# - $1 the container name
# - $2 the log file to report problems to
function build_image() {

    if [[ $1 == "" ]]; then
        echo "Image needs to be passed in argument"
        exit -2
    fi
	
	if [[ $2 == "" ]]; then
        echo "Log file needs to be passed in argument"
        exit -2
    fi

    if [[ -z "$NO_BASE_IMAGE" ]]; then
        echo " - Checking if base eskimo image is available"
        if [[ `docker images -q eskimo:base-eskimo_template 2>/dev/null` == "" ]]; then
            echo " - Trying to loads base eskimo image"
            for i in `ls -rt ../../packages_distrib/docker_template_base-eskimo*.tar.gz | tail -1`; do
                echo "   + loading image $i"
                gunzip -c $i | docker load >> $2 2>&1
                fail_if_error $? $2 -10
            done
        fi
    fi

    echo " - Deleting any previous containers"
    if [[ `docker ps -a -q -f name=$1` != "" ]]; then
        docker stop $1 > /dev/null 2>&1
        docker container rm $1 > /dev/null 2>&1
    fi

    # build
    echo " - building docker image $1"
    docker build --iidfile id_file --tag eskimo:$1_template .  >> $2 2>&1
    fail_if_error $? $2 -11

    export TMP_FOLDER=/tmp
    if [[ ! -z "$BUILD_TEMP_FOLDER" ]]; then
        export TMP_FOLDER=$BUILD_TEMP_FOLDER

        echo " - making sure I can write in $BUILD_TEMP_FOLDER"
        touch $BUILD_TEMP_FOLDER/test >> $2 2>&1
        fail_if_error $? $2 -11
    fi

    echo " - Starting container $1_template"
    # create and start container
    docker run \
            --privileged \
            -v $PWD:/scripts \
            -v $PWD/../common:/common  \
            -v $TMP_FOLDER:/tmp \
            -d --name $1 \
            -i \
            -t eskimo:$1_template bash  >> $2 2>&1
    fail_if_error $? $2 -12

}

# This function is to build a command wrapper around a command that needs to be called from a specific directory
# Arguments are:
# - $1 the source command to wrap
# - $2 the target wrapper to create
function create_binary_wrapper(){
    if [[ $1 == "" || $2 == "" ]]; then
        echo "source and target have to be passed as argument of the create_kafka_wrapper function"
    else
        touch $2
        chmod 777 $2
        echo -e '#!/bin/bash' > $2
        echo -e "" >> $2
        echo -e "__tmp_saved_dir=`pwd`" >> $2
        echo -e "function __tmp_returned_to_saved_dir() {" >> $2
        echo -e '     cd $__tmp_saved_dir' >> $2
        echo -e "}" >> $2
        echo -e "trap __tmp_returned_to_saved_dir 15" >> $2
        echo -e "trap __tmp_returned_to_saved_dir EXIT" >> $2
        echo -e "" >> $2
        echo -e "$1 \"\$@\"" >> $2
        echo -e "" >> $2
        echo -e "__tmp_returned_to_saved_dir" >> $2
        chmod 755 $2
    fi
}


function fail_if_error(){
    if [[ $1 != 0 ]]; then
        echo " -> failed !!"
        cat $2
        exit $3
    fi
}

function get_ip_address(){
    export IP_ADDRESS="`cat /etc/network/interfaces | grep address | cut -d ' ' -f 8`"
}

