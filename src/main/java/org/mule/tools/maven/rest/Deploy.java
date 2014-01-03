package org.mule.tools.maven.rest;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * @author Nicholas A. Stuart
 * @author Mohamed EL HABIB
 * @author Pascal Alma
 */
@Mojo(name = "deploy", requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDirectInvocation = true)
@Execute(phase = LifecyclePhase.DEPLOY, goal = "deploy")
public class Deploy extends AbstractMojo {
    public static final String DEFAULT_NAME = "MuleApplication";
    /**
     * Directory containing the generated Mule App.
     */
    @Parameter(property = "project.build.directory", required = true)
    protected File outputDirectory;
    /**
     * Name of the generated Mule App.
     */
    @Parameter(alias = "appName", property = "appName", defaultValue = "${project.build.finalName}")
    protected String finalName;
    /**
     * The name that the application will be deployed as. Default is
     * "MuleApplication"
     */
    @Parameter
    protected String name;
    /**
     * The version that the application will be deployed as. Default is the
     * current time in milliseconds.
     */
    @Parameter
    protected String version;
    /**
     * The username that has
     */
    @Parameter(required = true)
    protected String username;
    /**
     */
    @Parameter(required = true)
    protected String password;
    /**
     * Directory containing the app resources.
     */
    @Parameter(defaultValue = "${basedir}/src/main/app", required = true)
    protected File appDirectory;
    /**
     */
    @Parameter(required = true)
    protected URL muleApiUrl;
    /**
     */
    @Parameter(required = true)
    protected String serverGroup;

    private static final File getMuleZipFile(File outputDirectory, String filename) throws MojoFailureException {
        File file = new File(outputDirectory, filename + ".zip");
        if (!file.exists()) {
            throw new MojoFailureException("There is no application ZIP file generated : check that you have configured the maven-mule-plugin to generated the this file");
        }
        return file;
    }

    private static final void validateProject(File appDirectory) throws MojoExecutionException {
        File muleConfig = new File(appDirectory, "mule-config.xml");
        File deploymentDescriptor = new File(appDirectory, "mule-deploy.properties");

        if ((muleConfig.exists() == false) && (deploymentDescriptor.exists() == false)) {
            throw new MojoExecutionException("No mule-config.xml or mule-deploy.properties");
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        StaticLoggerBinder.getSingleton()
                .setLog(getLog());
        Logger logger = LoggerFactory.getLogger(getClass());

        if (name == null) {
            logger.info("Name is not set, using default \"{}\"", DEFAULT_NAME);
            name = DEFAULT_NAME;
        }
        if (version == null) {
            version = new SimpleDateFormat("MM-dd-yyyy-HH:mm:ss").format(Calendar.getInstance()
                    .getTime());
            logger.info("Version is not set, using a default of the timestamp: {}", version);
        }
        if (username == null || password == null) {
            throw new MojoFailureException((username == null ? "Username" : "Password") + " not set.");
        }
        if (outputDirectory == null) {
            throw new MojoFailureException("outputDirectory not set.");
        }
        if (finalName == null) {
            throw new MojoFailureException("finalName not set.");
        }
        if (serverGroup == null) {
            throw new MojoFailureException("serverGroup not set.");
        }
        try {
            validateProject(appDirectory);
            MuleRest muleRest = new MuleRest(muleApiUrl, username, password);

            if (version.contains("SNAPSHOT")) {
                String versionId = muleRest.restfullyGetApplicationIdByName(name, version);
                logger.info("About to remove versionId: " + versionId);
                if (versionId != null) {
                    muleRest.restfullyDeleteRepository(versionId);
                }
            }
            String versionId = muleRest.restfullyUploadRepository(name, version, getMuleZipFile(outputDirectory, finalName));
            String deploymentId = muleRest.restfullyCreateDeployment(serverGroup, name, versionId);
            muleRest.restfullyDeployDeploymentById(deploymentId);
        } catch (Exception e) {
            e.printStackTrace();
            throw new MojoFailureException("Error in attempting to deploy archive: " + e.toString(), e);
        }
    }

}