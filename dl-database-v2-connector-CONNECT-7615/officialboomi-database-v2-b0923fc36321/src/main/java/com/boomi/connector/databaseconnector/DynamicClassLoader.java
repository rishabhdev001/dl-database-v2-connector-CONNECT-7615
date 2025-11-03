package com.boomi.connector.databaseconnector;

import com.boomi.connector.databaseconnector.StreamHash;
//import com.google.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;


public class DynamicClassLoader {
    private final Logger logger;
    private final Map<String, ClassLoader> loaders;

    //@Inject
    public DynamicClassLoader() {
        logger = LoggerFactory.getLogger(DynamicClassLoader.class);
        loaders = new HashMap<>();
    }

    /**
     * Create a new instance of a class.  The class is loaded dynamically
     * from the file-system, unless it has already been loaded.
     * <p/>
     * The default constructor (no arguments) is used to create the instance.
     *
     * @param pathOrJarFile Path to the .class file's root directory, or the JAR file containing it.
     *                      Can be null/empty if the class is already in the classpath.
     * @param name          Name of the class, such as com.foo.Bar.
     */
    public synchronized Object createInstance(String pathOrJarFile, String name)
            throws IOException, ExceptionInInitializerError, ReflectiveOperationException {
        Class<?> clazz;
        if (StringUtils.isEmpty(pathOrJarFile)) {
            logger.info("entered if ");
            clazz = Class.forName(name);

        } else {
            logger.info("entered else ");
            ClassFileReference classFile = new ClassFileReference(new File(pathOrJarFile), name);

            String identifier = createClassFileIdentifier(classFile.getTargetFile());
            ClassLoader loader = loaders.get(identifier);
            if (null == loader) {
                loader = createClassLoader(classFile);
                loaders.put(identifier, loader);
            }

            clazz = Class.forName(classFile.getName(), true, loader);
        }

        return clazz.getConstructor().newInstance();
    }

    /**
     * Create a unique identifier for the given class file consisting of its
     * path and a hash of its contents.
     *
     * @param file Class file (.jar or .class).
     * @return Identifier for the class file.
     * @throws IOException Thrown when the file cannot be opened or read.
     */
    private String createClassFileIdentifier(File file) throws IOException {
        String hash = hashFile(file);
        String identifier = String.format("%s:%s", file.getAbsolutePath(), hash);
        return identifier;
    }

    private String hashFile(File file) throws IOException {
        StreamHash streamHash = new StreamHash();
        try (InputStream stream = new FileInputStream(file)) {
            return streamHash.computeSHA1String(stream);
        }
    }

    private ClassLoader createClassLoader(ClassFileReference classFile) throws MalformedURLException {
        ClassLoader loader;
        logger.info("Loading class {} from {}", classFile.getName(), classFile.getPathOrJar().getAbsolutePath());

        URL classUrl = classFile.getPathOrJar().getAbsoluteFile().toURI().toURL();
        loader = new ParentLastURLClassLoader(new URL[]{classUrl});
        return loader;
    }
}
