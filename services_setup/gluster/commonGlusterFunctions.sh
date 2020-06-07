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

# This script acts as a Watch Dog for docker containers when docker containers star background processes.
# When this happens, the docker container won't die unless the main process invoked by the run command - the one that
# keeps it waiting . stops.
# As a consequence, the background process have no chance to be restarted since the container won't ire if they crash
# unless the main process dies as well.
#
# Here comes the Watch Dog, it monitors a background process carefully and kills the main container process in case
# the background process dies.
# The Watch Dog takes both process IDs in argument
# - the first parameter is the PID is the background process to be monitored
# - the second parameter is
#   + either PID if the process to be killed in case the monitored background process vanishes
#   + or a command enabling to find that PID at runtime
# - A third argument is passed to log actions in the given log file
#
# A Watch Dog command should be invoked in background as well AFTER all the container process have been launched in
# background and BEFORE waiting on the main process.
# when using Watdh Dogs, even the main container process is started in background and "waited" (wait PID) in the end
# wait PID should be the last command in the startup script.


# Take care of deleting the gluster management lock file upon maintenance script exit
function delete_gluster_management_lock_file() {
    echo " - releasing gluster_management_lock"
    rm -Rf /var/lib/gluster/gluster_management_lock
}


# Convert list of hostnames passed in argument to IP addresses
function hostnames_to_ips () {

    if [[ $1 == "" ]]; then
        echo "Need to pass result of 'gluster pool list' to this function"
        return -1
    fi

    ip_addresses=""
    hostnames=`echo "$pool_list" | cut -d$'\t' -f 2 | sed  '/^$/d'`
    IFS=$'\n'
    for hostname in $hostnames; do
        hostname=`echo $hostname | xargs`
        #echo $hostname
        ip_address=`ping -c 1 $hostname 2>/dev/null  | grep PING | sed -E s/'PING ([^ ]+) \(([^ ]+)\).*'/'\2'/g`
        echo $ip_address
        ip_addresses=`echo -e "$ip_addresses\n$ip_address"`
    done

    echo "$ip_addresses"
}


# Gluster's "pool list" functions messes up between IP and hostnames
# As a design decision in Eskimo, we have decided to work under the hood solely with IP addresses.
# This function does a "gluster pool list" either locally or remotely and converts hostnames encountered with IP
# addresses
function get_pool_ips() {

    if [[ $1 != "" ]]; then
        export remote_gluster=$1
    else
        unset remote_gluster
    fi

    if [[ "$remote_gluster" == "" ]]; then

        for i in 1 2 3 4 5; do
            pool_list=`gluster pool list`
            if [[ $? != 0 ]]; then
                echo "    + listing gluster pool list failed - attempt $i" > /dev/stderr
                if [[ $i == 5 ]]; then
                    echo "    + Counld't get pool list in 5 attempts. crashing" > /dev/stderr
                    return 1
                fi
                sleep 2
            else
                break
            fi
        done

    else

        for i in 1 2 3 4 5; do
            pool_list=`gluster_call_remote.sh $remote_gluster pool list`
            if [[ $? != 0 ]]; then
                echo "    + listing remote gluster pool list on $remote_gluster failed - attempt $i" > /dev/stderr
                if [[ $i == 5 ]]; then
                    echo "    + Counld't get remote pool list in 5 attempts. crashing" > /dev/stderr
                    return 1
                fi
                sleep 2
            else
                break
            fi
        done
    fi

    hostnames_to_ips $pool_list
}

# This is used to dynamically elect a gluster node as a master in a consistent way accross node
function get_gluster_master () {

    # Inject topology
    . /etc/eskimo_topology.sh

    IFS=', ' read -r -a ALL_NODES <<< "$ALL_NODES_LIST_gluster"

    while [[ ${#ALL_NODES[@]} -ge 1 ]]; do

        CANDIDATE=${ALL_NODES[0]}

        if [[ "$CANDIDATE" != "$SELF_IP_ADDRESS" ]]; then

            ping -c 1 -W 5 $CANDIDATE > /tmp/ping_"$CANDIDATE".log 2>&1
            if [[ $? == 0 ]] ; then
                echo $CANDIDATE
                export GLUSTER_MASTER=$CANDIDATE
                return 0
            fi

        fi

        # remove first element from array
        ALL_NODES=("${ALL_NODES[@]:1}")
    done

    # If I reached this stage it means this node is eitehr the single one (single node cluster)
    # or the single onne answering
    echo $SELF_IP_ADDRESS
}