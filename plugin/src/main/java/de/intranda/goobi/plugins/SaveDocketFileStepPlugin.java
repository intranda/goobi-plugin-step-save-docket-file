package de.intranda.goobi.plugins;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
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

    private Path docketFile;

    private Path outputFile;

    private String mimeType;

    private int dotsPerInch;

    @Override
    public void initialize(Step step, String returnPath) {
        this.step = step;

        // read parameters from correct block in configuration file

        readConfiguration();

    }

    private void readConfiguration() {
        SubnodeConfiguration config = ConfigPlugins.getProjectAndStepConfig(title, step);

        String docketTemplateName = config.getString("/template/@file");
        if (StringUtils.isBlank(docketTemplateName)) {
            // error
        }
        docketFile = Paths.get(docketTemplateName);
        if (!docketFile.isAbsolute()) {
            docketFile = Paths.get(ConfigurationHelper.getInstance().getXsltFolder(), docketTemplateName);
        }

        if (!StorageProvider.getInstance().isFileExists(docketFile)) {
            // error
            log.error("The configured docket file '{}' does not exist.", docketFile.getFileName().toString());
        }
        log.trace("Using docket file '{}'", docketFile.getFileName().toString());

        mimeType = config.getString("/output/@format", "image/tiff");
        String fileName = config.getString("/output/@filename", "00000000.tif");
        String folder = config.getString("/output/@folder", "master");

        try {
            String destination = step.getProzess().getConfiguredImageFolder(folder);
            outputFile = Paths.get(destination, fileName);
        } catch (IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
        }

        log.trace("Using output file '{}'", outputFile);

        log.trace("Using mime type  '{}'", this.mimeType);

        // Get dots-per-inch (DPI) value
        dotsPerInch = config.getInt("//config/dotsPerInch", 150);

        log.trace("Using resolution of '{}", dotsPerInch);

    }

    @Override
    public PluginGuiType getPluginGuiType() {
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
        List<Process> processes = new ArrayList<>();
        processes.add(this.step.getProzess());
        XsltPreparatorDocket preparator = new XsltPreparatorDocket();
        try {
            XsltToPdf exporter = new XsltToPdf();
            FileOutputStream fileOutput = new FileOutputStream(outputFile.toFile());
            exporter.startExport(this.step.getProzess(), fileOutput, docketFile.toString(), preparator, mimeType, dotsPerInch);
            return PluginReturnValue.FINISH;
        } catch (IOException e) {
            log.error(e);
            return PluginReturnValue.ERROR;
        }
    }
}
