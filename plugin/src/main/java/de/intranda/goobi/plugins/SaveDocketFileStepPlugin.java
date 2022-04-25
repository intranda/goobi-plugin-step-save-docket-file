package de.intranda.goobi.plugins;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.fop.apps.MimeConstants;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.persistence.managers.DocketManager;
import io.goobi.workflow.xslt.XsltPreparatorDocket;
import io.goobi.workflow.xslt.XsltToPdf;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j2
public class SaveDocketFileStepPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_save_docket_file";
    @Getter
    private Step step;

    @Getter
    private boolean loadedConfigurationSuccessfully;
    @Getter
    private String docketFile;
    @Getter
    private String outputFile;
    @Getter
    private String mimeType;
    @Getter
    private int dotsPerInch;

    @Override
    public void initialize(Step step, String returnPath) {
        this.step = step;

        // read parameters from correct block in configuration file
        XMLConfiguration xml = ConfigPlugins.getPluginConfig(title);
        xml.setExpressionEngine(new XPathExpressionEngine());
        xml.setReloadingStrategy(new FileChangedReloadingStrategy());
        this.loadedConfigurationSuccessfully = this.loadConfiguration(xml);
        if (this.loadedConfigurationSuccessfully) {
            log.info("SaveDocketFile step plugin initialized");
        }
    }

    private boolean loadConfiguration(XMLConfiguration xml) {

        // Get docket file
        this.docketFile = this.getDocketFileFromConfiguration(xml);
        if (this.docketFile == null) {
            // Error message was already printed in getDocketFileFromConfiguration()
            return false;
        }
        StorageProviderInterface storage = StorageProvider.getInstance();
        if (!storage.isFileExists(Paths.get(this.docketFile))) {
            log.error("The specified docket file \"" + this.docketFile + "\" does not exist. (Plugin: " + this.title + ")");
            return false;
        }
        log.info("Using docket file \"" + this.docketFile + "\" in plugin: " + this.title);

        // Get output file
        this.outputFile = xml.getString("//config/output/@file", "");
        if (this.outputFile == null || this.outputFile.equals("")) {
            log.error("No output file specified for plugin: " + this.title);
            return false;
        }
        log.info("Using output file \"" + this.outputFile + "\" in plugin: " + this.title);

        // Get output mime type
        if (this.outputFile.endsWith(".pdf")) {
            this.mimeType = MimeConstants.MIME_PDF;
        } else if (outputFile.endsWith(".tiff")) {
            this.mimeType = MimeConstants.MIME_TIFF;
        } else {
            log.error("The specified output file \"" + this.outputFile + "\" has an invalid type. Supported are .pdf and .tiff");
            return false;
        }
        log.info("Using mime type file \"" + this.mimeType + "\" in plugin: " + this.title);

        // Get dots-per-inch (DPI) value
        this.dotsPerInch = xml.getInt("//config/dotsPerInch", -1);
        if (this.dotsPerInch == -1) {
            log.info("DPI is not specified for plugin \"" + this.title + "\". Default (300 DPI) is used.");
            this.dotsPerInch = 300;
        }
        log.info("Using resolution of \"" + this.dotsPerInch + "\" DPI in plugin: " + this.title);

        return true;
    }

    private String getDocketFileFromConfiguration(XMLConfiguration xml) {
        String xsltPath = ConfigurationHelper.getInstance().getXsltFolder();
        String file = xml.getString("//config/template/@file", null);
        if (file != null) {
            return file;
        }
        String name = xml.getString("//config/template/@name", null);
        if (name != null) {
            try {
                return xsltPath + DocketManager.getDocketByName(name).getFile();
            } catch (DAOException daoException) {
                log.error("Could not load docket file with name \"" + name + "\" in plugin: " + this.title);
                daoException.printStackTrace();
                return null;
            }
        }
        int id = xml.getInt("//config/template/@id", -1);
        if (id > -1) {
            try {
                return xsltPath + DocketManager.getDocketById(id).getFile();
            } catch (DAOException daoException) {
                log.error("Could not load docket file with id " + id + " in plugin: " + this.title);
                daoException.printStackTrace();
                return null;
            }
        }
        log.error("Could not load docket file from configuration for plugin: " + this.title);
        return null;
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        // return PluginGuiType.FULL;
        // return PluginGuiType.PART;
        // return PluginGuiType.PART_AND_FULL;
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return null;
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return null;
    }

    @Override
    public String finish() {
        return null;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {
        Process process = this.step.getProzess();
        int processId = process.getId();
        if (!this.loadedConfigurationSuccessfully) {
            String message = "Could not execute plugin \"" + this.title + "\" due to insufficient configuration!";
            log.error(message);
            Helper.addMessageToProcessLog(processId, LogType.ERROR, message);
            return PluginReturnValue.ERROR;
        }
        List<Process> processes = new ArrayList<>();
        processes.add(this.step.getProzess());
        XsltPreparatorDocket preparator = new XsltPreparatorDocket();
        try {
            XsltToPdf exporter = new XsltToPdf();
            FileOutputStream fileOutput = new FileOutputStream(new File(this.outputFile));
            // TODO: The method with attached mime type is available in newer Goobi Workflow core
            //exporter.startExport(this.step.getProzess(), fileOutput, this.docketFile, preparator, this.mimeType, this.dotsPerInch);
            exporter.startExport(this.step.getProzess(), fileOutput, this.docketFile, preparator);
            String message = "Created docket file successfully for process " + process.getTitel() + ".";
            log.error(message);
            Helper.addMessageToProcessLog(processId, LogType.INFO, message);
            return PluginReturnValue.FINISH;
        } catch (IOException ioException) {
            ioException.printStackTrace();
            String message = "Could not execute plugin \"" + this.title + "\". Please read the log files for more details.";
            log.error(message);
            Helper.addMessageToProcessLog(processId, LogType.ERROR, message);
            return PluginReturnValue.ERROR;
        }
    }
}
