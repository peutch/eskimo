/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
Author : eskimo.sh / https://www.eskimo.sh

Eskimo is available under a dual licensing model : commercial and GNU AGPL.
If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
any later version.
Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
commercial license.

Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Affero Public License for more details.

You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
Boston, MA, 02110-1301 USA.

You can be released from the requirements of the license by purchasing a commercial license. Buying such a
commercial license is mandatory as soon as :
- you develop activities involving Eskimo without disclosing the source code of your own product, software, 
  platform, use cases or scripts.
- you deploy eskimo as part of a commercial product, platform or software.
For more information, please contact eskimo.sh at https://www.eskimo.sh

The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
Software.
*/

const ESK_STRING_UPPER_CASE = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'];
const ESK_STRING_LOWER_CASE = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'];

function getHyphenSeparated (string) {
    let result = '';
    for (let i = 0; i < string.length; i++) {
        let strChar = string.charAt(i);
        let idx = ESK_STRING_UPPER_CASE.indexOf(strChar);
        if (idx >= 0) {
            if (i > 0) {
                result += "-";
            }
            result += ESK_STRING_LOWER_CASE[idx] ;
        } else {
            result += strChar;
        }
    }
    return result;
}

function getCamelCase(string) {
    return string.replace(/-([a-zA-Z])/g, function (m, w) {
        return w.toUpperCase();
    });
}

function getUcfirst(string) {
    return string.charAt(0).toUpperCase() + string.slice(1);
}

function noOp() {

}

$.fn.serializeObject = function() {
    let o = {};
    let a = this.serializeArray();
    $.each(a, function() {
        if (o[this.name]) {
            if (!o[this.name].push) {
                o[this.name] = [o[this.name]];
            }
            o[this.name].push(this.value || '');
        } else {
            o[this.name] = this.value || '';
        }
    });
    return o;
};

function errorHandler (jqXHR, status) {
    // error handler
    console.log(jqXHR);
    console.log (status);

    if (jqXHR.status == "401") {
        window.location = "login.html";
    }

    if (jqXHR && jqXHR.responseJSON  && jqXHR.responseJSON.message) {
        alert('fail : ' + jqXHR.responseJSON.message);

    } else if (jqXHR && jqXHR.responseJSON  && jqXHR.responseJSON.error) {
        alert('fail : ' + jqXHR.responseJSON.error);

    } else {
        console.error('fail : ' + status);
    }
}

function isFunction(functionToCheck) {
    if (!functionToCheck) {
        return false;
    }
    return {}.toString.call(functionToCheck) === '[object Function]';
}
