/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */

package ch.niceideas.eskimo.model;

import lombok.Getter;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class JSONInstallOpCommand<T extends Serializable> implements JSONOpCommand {

    @Getter
    private final ArrayList<T> installations = new ArrayList<>();

    @Getter
    private final ArrayList<T> uninstallations = new ArrayList<>();

    @Getter
    private final ArrayList<T> restarts = new ArrayList<>();

    void addInstallation(T service) {
        installations.add(service);
    }

    void addUninstallation(T service) {
        uninstallations.add(service);
    }

    protected void addRestart (T service) {
        restarts.add (service);
    }

    public boolean hasChanges() {
        return !getInstallations().isEmpty() || !getUninstallations().isEmpty() || !getRestarts().isEmpty();
    }

    public void sortRestarts (Comparator<T> c) {
        restarts.sort(c);
    }

    protected Collection<Object> toJsonList(List<? extends SimpleOperationCommand.SimpleOperationId> listOfPairs) {
        return listOfPairs.stream()
                .map((Function<SimpleOperationCommand.SimpleOperationId, Object>) id -> {
                    JSONObject ret = new JSONObject();
                    try {
                        ret.put(id.getService(), id.getNode());
                    } catch (JSONException e) {
                        // cannot happen
                    }
                    return ret;
                })
                .collect(Collectors.toList());
    }
}
