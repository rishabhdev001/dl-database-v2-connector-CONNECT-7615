package com.boomi.connector.databaseconnector;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;


import static com.boomi.connector.databaseconnector.JavaUtility.log;

public class ClassFileReference
{
    private final Logger logger;
    private final File pathOrJar;
    private final String name;
    private final File targetFile;

    public ClassFileReference(File pathOrJar, String name)
    {
        logger = LoggerFactory.getLogger(ClassFileReference.class);

        this.pathOrJar = pathOrJar;
        this.name = name;

        if (pathOrJar.isDirectory())
        {
            String relativePathToClassFile =
                    StringUtils.replace(name, ".", String.valueOf(IOUtils.DIR_SEPARATOR)) + ".class";
            targetFile = new File(pathOrJar, relativePathToClassFile);
        }
        else if (StringUtils.endsWithIgnoreCase(pathOrJar.getAbsolutePath(), ".jar"))
        {
            targetFile = pathOrJar;
        }
        else
        {
            throw log(logger, new IllegalArgumentException(
                    "Class file reference '" + pathOrJar.getAbsolutePath() + "' must be a directory or a JAR file"));
        }
    }

    public File getPathOrJar()
    {
        return pathOrJar;
    }

    public String getName()
    {
        return name;
    }

    public File getTargetFile()
    {
        return targetFile;
    }
}
