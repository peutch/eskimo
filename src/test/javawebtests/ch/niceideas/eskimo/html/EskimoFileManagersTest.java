/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.html;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;

public class EskimoFileManagersTest extends AbstractWebTest {

    private String dirContent = null;

    @Before
    public void setUp() throws Exception {

        dirContent = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoFileManagersTest/dirContentTest.json"));

        loadScript (page, "eskimoFileManagers.js");

        page.executeJavaScript("function errorHandler() {};");

        page.executeJavaScript("var dirContent = " + dirContent + "");

        // instantiate test object
        page.executeJavaScript("eskimoFileManagers = new eskimo.FileManagers({" +
                "    eskimoMain: {" +
                "        isSetupDone: function() { return true; }," +
                "        showOnlyContent: function() {}," +
                "        hideProgressbar: function() {}" +
                "    }" +
                "});");

        waitForElementIdInDOM("file-upload-progress-modal");

        // mock functions
        page.executeJavaScript("eskimo.FileManagers.submitFormFileUpload = function (e) {}");

        // set services for tests
        page.executeJavaScript("eskimoFileManagers.setAvailableNodes (" +
                "[{\"nbr\": 1, \"nodeName\": \"192-168-10-11\", \"nodeAddress\": \"192.168.10.11\"}, " +
                " {\"nbr\": 2, \"nodeName\": \"192-168-10-13\", \"nodeAddress\": \"192.168.10.13\"} ] );");

        page.executeJavaScript("eskimoFileManagers.openFolder = function (nodeAddress, nodeName, currentFolder, subFolder) {" +
                "    eskimoFileManagers.listFolder (nodeAddress, nodeName, currentFolder+'/'+subFolder, dirContent);\n" +
                "}");

        page.executeJavaScript("eskimoFileManagers.connectFileManager = function (nodeAddress, nodeName) {" +
                "    alert (\"calledFor : \" + nodeAddress);\n" +
                "    eskimoFileManagers.getOpenedFileManagers().push({\"nodeName\" : nodeName, \"nodeAddress\": nodeAddress, \"current\": \"/\"});" +
                "    eskimoFileManagers.listFolder (nodeAddress, nodeName, '/', dirContent);\n" +
                "}");

        page.executeJavaScript("$('#inner-content-file-managers').css('display', 'inherit')");
        page.executeJavaScript("$('#inner-content-file-managers').css('visibility', 'visible')");
    }

    @Test
    public void testNominal() throws Exception {

        page.executeJavaScript("eskimoFileManagers.openFileManager('192.168.10.11', '192-168-10-11');");

        assertJavascriptEquals("1.0", "eskimoFileManagers.getOpenedFileManagers().length");
        assertJavascriptEquals("192-168-10-11", "eskimoFileManagers.getOpenedFileManagers()[0].nodeName");

        // if this is set then we want as far as listFolder function
        assertJavascriptEquals("/", "eskimoFileManagers.getOpenedFileManagers()[0].current");
    }

    @Test
    public void testNodeVanish() throws Exception {
        fail ("Assess when a node vanish opened file manager is disabled");
    }

    @Test
    public void testFindFileManager() throws Exception {

        page.executeJavaScript("eskimoFileManagers.openFileManager('192.168.10.11', '192-168-10-11');");
        page.executeJavaScript("eskimoFileManagers.openFileManager('192.168.10.13', '192-168-10-13');");

        // if this is set then we want as far as listFolder function
        assertJavascriptEquals("/", "eskimoFileManagers.getOpenedFileManagers()[0].current");
        assertJavascriptEquals("/", "eskimoFileManagers.getOpenedFileManagers()[1].current");

        assertJavascriptEquals("192.168.10.11", "eskimoFileManagers.findFileManager('192-168-10-11').nodeAddress");
        assertJavascriptEquals("192.168.10.13", "eskimoFileManagers.findFileManager('192-168-10-13').nodeAddress");
    }

    @Test
    public void testUpdateCurrentFolder() throws Exception {

        page.executeJavaScript("eskimoFileManagers.openFileManager('192.168.10.11', '192-168-10-11');");
        page.executeJavaScript("eskimoFileManagers.openFileManager('192.168.10.13', '192-168-10-13');");

        page.executeJavaScript("eskimoFileManagers.updateCurrentFolder('192-168-10-11', 'test1');");
        page.executeJavaScript("eskimoFileManagers.updateCurrentFolder('192-168-10-13', 'test2');");

        assertJavascriptEquals("test1", "eskimoFileManagers.getOpenedFileManagers()[0].current");
        assertJavascriptEquals("test2", "eskimoFileManagers.getOpenedFileManagers()[1].current");
    }

    @Test
    public void testListFolder() throws Exception {

        page.executeJavaScript("eskimoFileManagers.openFileManager('192.168.10.11', '192-168-10-11');");

        page.executeJavaScript("eskimoFileManagers.listFolder('192.168.10.11', '192-168-10-11', '/', "+dirContent+");");

        String htmlContent = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoFileManagersTest/expectedContent.rawhtml"));

        assertJavascriptEquals(htmlContent, "$('#file-manager-folder-content-192-168-10-11').html()");
    }

    @Test
    public void testShowParent() throws Exception {

        page.executeJavaScript("eskimoFileManagers.openFileManager('192.168.10.11', '192-168-10-11');");

        page.executeJavaScript("eskimoFileManagers.getOpenedFileManagers()[0].current = '/home/eskimo'");

        page.executeJavaScript("eskimoFileManagers.showParent('192.168.10.11', '192-168-10-11');");

        assertJavascriptEquals("/home/.", "eskimoFileManagers.getOpenedFileManagers()[0].current");
    }

    @Test
    public void testShowPrevious() throws Exception {

        testShowParent();

        page.executeJavaScript("eskimoFileManagers.showPrevious('192.168.10.11', '192-168-10-11');");

        assertJavascriptEquals("/home/eskimo/.", "eskimoFileManagers.getOpenedFileManagers()[0].current");
    }

    @Test
    public void testCloseFileManager() throws Exception {

        page.executeJavaScript("eskimoFileManagers.showFileManagers()");

        page.executeJavaScript("eskimoFileManagers.openFileManager('192.168.10.11', '192-168-10-11');");
        page.executeJavaScript("eskimoFileManagers.openFileManager('192.168.10.13', '192-168-10-13');");

        page.executeJavaScript("eskimoFileManagers.selectFileManager('192.168.10.11', '192-168-10-11');");

        //System.err.println (page.asXml());

        waitForElementIdInDOM("file-manager-close-192-168-10-11");

        page.getElementById("file-manager-close-192-168-10-11").click();

        assertJavascriptEquals("1.0", "eskimoFileManagers.getOpenedFileManagers().length");
        assertJavascriptEquals("192-168-10-13", "eskimoFileManagers.getOpenedFileManagers()[0].nodeName");
    }

    @Test
    public void testShowFileManagers() {
        page.executeJavaScript("eskimoFileManagers.showFileManagers()");

        assertNotNull (page.getElementById("file_manager_open_192-168-10-11"));
        assertEquals ("192.168.10.11", page.getElementById("file_manager_open_192-168-10-11").getTextContent().trim());

        assertNotNull (page.getElementById("file_manager_open_192-168-10-13"));
        assertEquals ("192.168.10.13", page.getElementById("file_manager_open_192-168-10-13").getTextContent().trim());
    }

    @Test
    public void testClickOpenFileManagers() throws Exception {

        testShowFileManagers();

        page.getElementById("file_manager_open_192-168-10-13").click();

        //System.err.println (page.asXml());

        assertCssValue ("#file-managers-file-manager-192-168-10-13", "visibility", "inherit");
        assertCssValue ("#file-managers-file-manager-192-168-10-13", "display", "inherit");

        page.getElementById("file_manager_open_192-168-10-11").click();

        assertCssValue ("#file-managers-file-manager-192-168-10-11", "visibility", "inherit");
        assertCssValue ("#file-managers-file-manager-192-168-10-11", "display", "inherit");

        assertCssValue ("#file-managers-file-manager-192-168-10-13", "visibility", "hidden");
        assertCssValue ("#file-managers-file-manager-192-168-10-13", "display", "none");
    }
}
