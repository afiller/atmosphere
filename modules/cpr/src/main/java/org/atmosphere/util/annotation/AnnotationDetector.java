/*
 * Copyright 2015 Async-IO.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *//* AnnotationDetector.java
  *
  * Created: 2011-10-10 (Year-Month-Day)
  * Character encoding: UTF-8
  *
  ****************************************** LICENSE *******************************************
  *
  * Copyright (c) 2011 - 2013 XIAM Solutions B.V. (http://www.xiam.nl)
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *      http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package org.atmosphere.util.annotation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * {@code AnnotationDetector} reads Java Class File (".class") files and reports the
 * encountered annotations via a simple, developer friendly API.
 * <p/>
 * A Java Class File consists of a stream of 8-bit bytes. All 16-bit, 32-bit, and 64-bit
 * quantities are constructed by reading in two, four, and eight consecutive 8-bit
 * bytes, respectively. Multi byte data items are always stored in big-endian order,
 * where the high bytes come first. In the Java and Java 2 platforms, this format is
 * supported by interfaces {@link java.io.DataInput} and {@link java.io.DataOutput}.
 * <p/>
 * A class file consists of a single ClassFile structure:
 * <pre>
 * ClassFile {
 *   u4 magic;
 *   u2 minor_version;
 *   u2 major_version;
 *   u2 constant_pool_count;
 *   cp_info constant_pool[constant_pool_count-1];
 *   u2 access_flags;
 *   u2 this_class;
 *   u2 super_class;
 *   u2 interfaces_count;
 *   u2 interfaces[interfaces_count];
 *   u2 fields_count;
 *   field_info fields[fields_count];
 *   u2 methods_count;
 *   method_info methods[methods_count];
 *   u2 attributes_count;
 *   attribute_info attributes[attributes_count];
 * }
 *
 * Where:
 * u1 unsigned byte {@link java.io.DataInput#readUnsignedByte()}
 * u2 unsigned short {@link java.io.DataInput#readUnsignedShort()}
 * u4 unsigned int {@link java.io.DataInput#readInt()}
 *
 * Annotations are stored as Attributes (i.e. "RuntimeVisibleAnnotations" and
 * "RuntimeInvisibleAnnotations").
 * </pre>
 * References:
 * <ul>
 * <li><a href="http://en.wikipedia.org/wiki/Java_class_file">Java class file (Wikipedia)</a>
 * (Gentle Introduction);
 * <li><a href="http://download.oracle.com/otndocs/jcp/jcfsu-1.0-fr-eval-oth-JSpec/">Class
 * File Format Specification</a> (Java 6 version) and the
 * <a href="http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html">Java
 * VM Specification (Chapter 4)</a> for the real work.
 * <li><a href="http://stackoverflow.com/questions/259140">scanning java annotations at
 * runtime</a>.
 * </ul>
 * <p/>
 * Similar projects / libraries:
 * <ul>
 * <li><a href="http://community.jboss.org/wiki/MCScanninglib">JBoss MC Scanning lib</a>;
 * <li><a href="http://code.google.com/p/reflections/">Google Reflections</a>, in fact an
 * improved version of <a href="http://scannotation.sourceforge.net/">scannotation</a>;
 * <li><a herf="https://github.com/ngocdaothanh/annovention">annovention</a>, improved version
 * of the <a href="http://code.google.com/p/annovention">original Annovention</a> project.
 * Available from maven: {@code tv.cntt:annovention:1.2};
 * <li>If using the Spring Framework, use {@code ClassPathScanningCandidateComponentProvider}
 * </ul>
 * <p/>
 * All above mentioned projects make use of a byte code manipulation library (like BCEL,
 * ASM or Javassist).
 *
 * @author <a href="mailto:rmuller@xiam.nl">Ronald K. Muller</a>
 * @since annotation-detector 3.0.0
 */
public final class AnnotationDetector {

    /**
     * {@code Reporter} is the base interface, used to report the detected annotations.
     * Every category of annotations (i.e. Type, Field and Method) has its own specialized
     * interface. This enables an efficient way of reporting the detected annotations.
     */
    public interface Reporter {

        /**
         * Return the {@code Annotation} classes which must be reported (all other
         * annotations are skipped).
         */
        Class<? extends Annotation>[] annotations();

    }

    /**
     * A {@code Reporter} for type annotations.
     */
    public interface TypeReporter extends Reporter {

        /**
         * This call back method is used to report an type level {@code Annotation}.
         * Only {@code Annotation}s, specified by {@link #annotations()} are reported!
         */
        void reportTypeAnnotation(Class<? extends Annotation> annotation, String className);

    }

    /**
     * A {@code Reporter} for field annotations.
     */
    public interface FieldReporter extends Reporter {

        /**
         * This call back method is used to report an field level {@code Annotation}.
         * Only {@code Annotation}s, specified by {@link #annotations()} are reported!
         */
        void reportFieldAnnotation(Class<? extends Annotation> annotation, String className,
                                   String fieldName);

    }

    /**
     * A {@code Reporter} for method annotations.
     */
    public interface MethodReporter extends Reporter {

        /**
         * This call back method is used to report an method level {@code Annotation}.
         * Only {@code Annotation}s, specified by {@link #annotations()} are reported!
         */
        void reportMethodAnnotation(Class<? extends Annotation> annotation, String className,
                                    String methodName);

    }

    private final static Logger logger = LoggerFactory.getLogger(AnnotationDetector.class);

    // Constant Pool type tags
    private static final int CP_UTF8 = 1;
    private static final int CP_INTEGER = 3;
    private static final int CP_FLOAT = 4;
    private static final int CP_LONG = 5;
    private static final int CP_DOUBLE = 6;
    private static final int CP_CLASS = 7;
    private static final int CP_STRING = 8;
    private static final int CP_REF_FIELD = 9;
    private static final int CP_REF_METHOD = 10;
    private static final int CP_REF_INTERFACE = 11;
    private static final int CP_NAME_AND_TYPE = 12;
    private static final int CP_METHOD_HANDLE = 15;
    private static final int CP_METHOD_TYPE = 16;
    private static final int CP_INVOKE_DYNAMIC = 18;

    // AnnotationElementValue
    private static final int BYTE = 'B';
    private static final int CHAR = 'C';
    private static final int DOUBLE = 'D';
    private static final int FLOAT = 'F';
    private static final int INT = 'I';
    private static final int LONG = 'J';
    private static final int SHORT = 'S';
    private static final int BOOLEAN = 'Z';
    // used for AnnotationElement only
    private static final int STRING = 's';
    private static final int ENUM = 'e';
    private static final int CLASS = 'c';
    private static final int ANNOTATION = '@';
    private static final int ARRAY = '[';

    // The buffer is reused during the life cycle of this AnnotationDetector instance
    private final ClassFileBuffer cpBuffer = new ClassFileBuffer();
    // the annotation types to report, see {@link #annotations()}
    private final Map<String, Class<? extends Annotation>> annotations;

    private TypeReporter typeReporter;
    private FieldReporter fieldReporter;
    private MethodReporter methodReporter;

    // the 'raw' name of this interface or class (using '/' instead of '.' in package name)
    private String typeName;
    // Reusing the constantPool is not needed for better performance
    private Object[] constantPool;
    private String memberName;

    /**
     * Create a new {@code AnnotationDetector}, reporting the detected annotations
     * to the specified {@code Reporter}.
     */
    public AnnotationDetector(final Reporter reporter) {

        final Class<? extends Annotation>[] a = reporter.annotations();
        annotations = new HashMap<String, Class<? extends Annotation>>(a.length);
        // map "raw" type names to Class object
        for (int i = 0; i < a.length; ++i) {
            annotations.put("L" + a[i].getName().replace('.', '/') + ";", a[i]);
        }
        if (reporter instanceof TypeReporter) {
            typeReporter = (TypeReporter) reporter;
        }
        if (reporter instanceof FieldReporter) {
            fieldReporter = (FieldReporter) reporter;
        }
        if (reporter instanceof MethodReporter) {
            methodReporter = (MethodReporter) reporter;
        }
        if (typeReporter == null && fieldReporter == null && methodReporter == null) {
            throw new AssertionError("No reporter defined");
        }
    }

    /**
     * Report all Java ClassFile files available on the class path.
     *
     * @see #detect(File...)
     */
    public void detect() throws IOException {
        detect(new ClassFileIterator());
    }

    /**
     * Report all Java ClassFile files available on the class path within
     * the specified packages and sub packages.
     *
     * @see #detect(File...)
     */
    public final void detect(final String... packageNames) throws IOException {
        final String[] pkgNameFilter = new String[packageNames.length];
        for (int i = 0; i < pkgNameFilter.length; ++i) {
            pkgNameFilter[i] = packageNames[i].replace('.', '/');
            if (!pkgNameFilter[i].endsWith("/")) {
                pkgNameFilter[i] = pkgNameFilter[i].concat("/");
            }

        }
        final Set<File> files = new HashSet<File>();
        final Set<InputStream> streams = new HashSet<InputStream>();

        for (final String packageName : pkgNameFilter) {
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
            final Enumeration<URL> resourceEnum = loader.getResources(packageName);
            while (resourceEnum.hasMoreElements()) {
                final URL url = resourceEnum.nextElement();
                // Handle JBoss VFS URL's which look like (example package 'nl.dvelop'):
                // vfs:/foo/bar/website.war/WEB-INF/classes/nl/dvelop/
                // vfs:/foo/bar/website.war/WEB-INF/lib/dwebcore-0.0.1.jar/nl/dvelop/
                // Different vfs protocols include vfs, vfsfile, vfszip, vfsjar, and vfsmemory
                final boolean isVfs = url.getProtocol() != null && url.getProtocol().startsWith("vfs");
                if ("file".equals(url.getProtocol()) || isVfs) {
                    final File dir = toFile(url);
                    if (dir.isDirectory()) {
                        files.add(dir);
                        print("Add directory: '%s'", dir);
                    } else if (isVfs) {
                        //Jar file via JBoss VFS protocol - strip package name
                        String jarPath = dir.getPath();
                        final int idx = jarPath.indexOf(".jar");
                        if (idx > -1) {
                            jarPath = jarPath.substring(0, idx + 4);
                            final File jarFile = new File(jarPath);
                            if (jarFile.isFile() && jarFile.exists()) {
                                files.add(jarFile);
                                print("Add jar file from VFS: '%s'", jarFile);
                            } else {
                                try {
                                    // VirtualFile#getChildren(java.lang.String) may return an object which refers a .jar managed by the deployer
                                    // The problem is that this .jar file does not contains .class in sub-directories
                                    // Ex: if your original file contains /foo/bar/Baz.class and /foo/Bar.class, VFS returns a .jar file with:
                                    // - /foo
                                    // - /foo/Bar.class
                                    // - /foo/bar
                                    // ==> /foo/bar/Baz.class is missing!
                                    // Resolving child directories recursively solves the issue
                                    List<org.jboss.vfs.VirtualFile> vfs = getVfsChildren(org.jboss.vfs.VFS.getChild(dir.getPath()));
                                    for (org.jboss.vfs.VirtualFile f : vfs) {
                                        files.add(f.getPhysicalFile());
                                    }
                                } catch (Throwable ex) {
                                    vfs(url, packageName, streams);
                                }
                            }
                        } else {
                            vfs(url, packageName, streams);
                        }
                    }
                } else if (isRunningJavaWebStart()) {
                    try {
                        loadJarContent((JarURLConnection) url.openConnection(), packageName, streams);
                    } catch (ClassCastException cce) {
                        throw new AssertionError("Not a File: " + url.toExternalForm());
                    }
                } else {
                    // Resource in Jar File
                    File jarFile;

                    try {
                        jarFile = toFile(((JarURLConnection) url.openConnection()).getJarFileURL());
                    } catch (ClassCastException cce) {
                        try {
                            // Weblogic crap
                            String u = url.toExternalForm();
                            if (u.startsWith("zip:")) {
                                u = u.substring(4);
                                if (!u.startsWith("file:")) {
                                    u = "file:" + u;
                                }
                                u = u.substring(0, u.indexOf("!"));
                            }
                            jarFile = toFile(new URL(u));
                        } catch (Exception ex) {
                            throw new AssertionError("Not a File: " + url.toExternalForm());
                        }
                    }

                    try {
                        if (jarFile.isFile()) {
                            files.add(jarFile);
                            print("Add jar file: '%s'", jarFile);
                        } else {
                            final URLConnection urlConnection = url.openConnection();
                            if (urlConnection instanceof JarURLConnection) {
                                loadJarContent((JarURLConnection) (url.openConnection()), packageName, streams);
                            } else {
                                streams.add(url.openConnection().getInputStream());
                            }
                        }
                    } catch (Exception ex) {
                        print("Cannot load from jar file", ex);
                    }
                }
            }
        }

        if (!files.isEmpty()) {
            detect(new ClassFileIterator(files.toArray(new File[files.size()]), pkgNameFilter));
        } else if (!streams.isEmpty()) {
            detect(new ClassFileIterator(streams.toArray(new InputStream[streams.size()]), pkgNameFilter));
        }
    }

    /**
     * <p>
     * This method recursively retrieves VFS files when a directory is detected.
     * </p>
     *
     * @param vfs the root
     * @return all children files
     */
    private List<org.jboss.vfs.VirtualFile> getVfsChildren(final org.jboss.vfs.VirtualFile vfs) {
        final List<org.jboss.vfs.VirtualFile> retval = new ArrayList<org.jboss.vfs.VirtualFile>();

        for (org.jboss.vfs.VirtualFile f : vfs.getChildren()) {
            if (f.isDirectory()) {
                retval.addAll(getVfsChildren(org.jboss.vfs.VFS.getChild(vfs.getPathName() + File.separator + f.getName())));
            } else {
                retval.add(f);
            }
        }

        return retval;
    }

    private boolean isRunningJavaWebStart() {
        boolean hasJNLP = false;
        try {
            Class.forName("javax.jnlp.ServiceManager");
            hasJNLP = true;
        } catch (ClassNotFoundException ex) {
            hasJNLP = false;
        }
        return hasJNLP;
    }

    private void loadJarContent(JarURLConnection url, String packageName, Set<InputStream> streams) throws IOException {
        // Using a JarURLConnection will load the JAR from the cache when using Webstart 1.6
        // In Webstart 1.5, the URL will point to the cached JAR on the local filesystem
        JarFile jarFile = url.getJarFile();
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().startsWith(packageName)) {
                streams.add(jarFile.getInputStream(entry));
            }
        }
    }

    private void vfs(URL url, String packageName, Set<InputStream> streams) {
//        org.jboss.virtual.VFS vfs = org.jboss.virtual.VFS.getVFS(url);
//        org.jboss.virtual.VirtualFile root = vfs.getRoot();
//        List<org.jboss.virtual.VirtualFile> children = root.getChildrenRecursively();
//        ClassLoader c = Thread.currentThread().getContextClassLoader();
//        for (org.jboss.virtual.VirtualFile f : children) {
//            String clazz = f.getPathName();
//            String classP = (packageName + clazz);
//            if (clazz != null && clazz.endsWith(".class")) {
//                streams.add(c.getResourceAsStream(classP));
//            }
//        }
        ClassLoader c = Thread.currentThread().getContextClassLoader();

        try {
            Class<?> vfs = c.loadClass("org.jboss.virtual.VFS");
            Method getVFS = vfs.getMethod("getVFS", new Class[]{URL.class});
            Object vfsInstance = getVFS.invoke(null, url);
            Method getRoot = vfs.getMethod("getRoot");

            Object virtualFileInstance = getRoot.invoke(vfsInstance);
            Method getChildrenRecursively = virtualFileInstance.getClass().getMethod("getChildrenRecursively");
            List<Object> children = (List<Object>) getChildrenRecursively.invoke(virtualFileInstance);
            Method getPathName = virtualFileInstance.getClass().getMethod("getPathName");
            for (Object f : children) {
                String clazz = (String) getPathName.invoke(f);
                String classP = (packageName + clazz);
                if (clazz != null && clazz.endsWith(".class")) {
                    streams.add(c.getResourceAsStream(classP));
                }
            }
        } catch (Throwable t) {
            logger.trace("", t);
        }
    }

    /**
     * Report all Java ClassFile files available from the specified files
     * and/or directories, including sub directories.
     * <p/>
     * Note that non-class files (files, not starting with the magic number
     * {@code CAFEBABE} are silently ignored.
     */
    public void detect(final File... filesOrDirectories) throws IOException {
        print("detectFilesOrDirectories: %s", (Object) filesOrDirectories);
        detect(new ClassFileIterator(filesOrDirectories, null));
    }

    // private
    private File toFile(final URL url) throws MalformedURLException {
        // only correct way to convert the URL to a File object, also see issue #16
        // Do not use URLDecoder
        try {
            return new File(url.toURI());
        } catch (URISyntaxException ex) {
            throw new MalformedURLException(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            try {
                return new File(URLDecoder.decode(url.getFile(), "UTF-8"));
            } catch (Exception ex2) {
                throw new MalformedURLException(ex.getMessage());
            }
        }
    }

    private void detect(final ClassFileIterator iterator) throws IOException {
        InputStream stream;
        while ((stream = iterator.next()) != null) {
            try {
                cpBuffer.readFrom(stream);
                if (hasCafebabe(cpBuffer)) {
                    detect(cpBuffer);
                } // else ignore
            } catch (Throwable t) { // SUPPRESS CHECKSTYLE IllegalCatchCheck
                // catch all errors
                if (!iterator.isFile()) {
                    // in case of an error we close the ZIP File here
                    stream.close();
                }
            } finally {
                // closing InputStream from ZIP Entry is handled by ZipFileIterator
                if (iterator.isFile()) {
                    stream.close();
                }
            }
        }
    }

    private boolean hasCafebabe(final ClassFileBuffer buffer) throws IOException {
        return buffer.size() > 4 && buffer.readInt() == 0xCAFEBABE;
    }

    /**
     * Inspect the given (Java) class file in streaming mode.
     */
    private void detect(final DataInput di) throws IOException {
        readVersion(di);
        readConstantPoolEntries(di);
        readAccessFlags(di);
        readThisClass(di);
        readSuperClass(di);
        readInterfaces(di);
        readFields(di);
        readMethods(di);
        readAttributes(di, 'T', typeReporter == null);
    }

    private void readVersion(final DataInput di) throws IOException {
        // sequence: minor version, major version (argument_index is 1-based)
        if (logger.isDebugEnabled()) {
            print("Java Class version %2$d.%1$d",
                    di.readUnsignedShort(), di.readUnsignedShort());
        } else {
            di.skipBytes(4);
        }
    }

    private void readConstantPoolEntries(final DataInput di) throws IOException {
        final int count = di.readUnsignedShort();
        constantPool = new Object[count];
        for (int i = 1; i < count; ++i) {
            if (readConstantPoolEntry(di, i)) {
                // double slot
                ++i;
            }
        }
    }

    /**
     * Return true if a double slot is read (in case of Double or Long constant).
     */
    private boolean readConstantPoolEntry(final DataInput di, final int index)
            throws IOException {

        final int tag = di.readUnsignedByte();
        switch (tag) {
            case CP_UTF8:
                constantPool[index] = di.readUTF();
                return false;
            case CP_INTEGER:
                di.skipBytes(4); // readInt()
                return false;
            case CP_FLOAT:
                di.skipBytes(4); // readFloat()
                return false;
            case CP_LONG:
                di.skipBytes(8); // readLong()
                return true;
            case CP_DOUBLE:
                di.skipBytes(8); // readDouble()
                return true;
            case CP_CLASS:
            case CP_STRING:
                // reference to CP_UTF8 entry. The referenced index can have a higher number!
                constantPool[index] = di.readUnsignedShort();
                return false;
            case CP_REF_FIELD:
            case CP_REF_METHOD:
            case CP_REF_INTERFACE:
            case CP_NAME_AND_TYPE:
                di.skipBytes(4);  // readUnsignedShort() * 2
                return false;
            case CP_METHOD_HANDLE:
                di.skipBytes(3);
                return false;
            case CP_METHOD_TYPE:
                di.skipBytes(2);  // readUnsignedShort()
                return false;
            case CP_INVOKE_DYNAMIC:
                di.skipBytes(4);  // readUnsignedShort() * 2
                return false;
            default:
                throw new ClassFormatError(
                        "Unkown tag value for constant pool entry: " + tag);
        }
    }

    private void readAccessFlags(final DataInput di) throws IOException {
        di.skipBytes(2); // u2
    }

    private void readThisClass(final DataInput di) throws IOException {
        typeName = resolveUtf8(di);
        print("read type '%s'", typeName);
    }

    private void readSuperClass(final DataInput di) throws IOException {
        di.skipBytes(2); // u2
    }

    private void readInterfaces(final DataInput di) throws IOException {
        final int count = di.readUnsignedShort();
        di.skipBytes(count * 2); // count * u2
    }

    private void readFields(final DataInput di) throws IOException {
        final int count = di.readUnsignedShort();
        print("field count = %d", count);
        for (int i = 0; i < count; ++i) {
            readAccessFlags(di);
            memberName = resolveUtf8(di);
            final String descriptor = resolveUtf8(di);
            readAttributes(di, 'F', fieldReporter == null);
            print("Field: %s, descriptor: %s", memberName, descriptor);
        }
    }

    private void readMethods(final DataInput di) throws IOException {
        final int count = di.readUnsignedShort();
        {
            print("method count = %d", count);
        }
        for (int i = 0; i < count; ++i) {
            readAccessFlags(di);
            memberName = resolveUtf8(di);
            final String descriptor = resolveUtf8(di);
            readAttributes(di, 'M', methodReporter == null);
            print("Method: %s, descriptor: %s", memberName, descriptor);
        }
    }

    private void readAttributes(final DataInput di, final char reporterType,
                                final boolean skipReporting) throws IOException {

        final int count = di.readUnsignedShort();
        print("attribute count (%s) = %d", reporterType, count);
        for (int i = 0; i < count; ++i) {
            final String name = resolveUtf8(di);
            // in bytes, use this to skip the attribute info block
            final int length = di.readInt();
            if (!skipReporting &&
                    ("RuntimeVisibleAnnotations".equals(name) ||
                            "RuntimeInvisibleAnnotations".equals(name))) {
                readAnnotations(di, reporterType);
            } else {
                print("skip attribute %s", name);
                di.skipBytes(length);
            }
        }
    }

    private void readAnnotations(final DataInput di, final char reporterType)
            throws IOException {

        // the number of Runtime(In)VisibleAnnotations
        final int count = di.readUnsignedShort();
        print("annotation count (%s) = %d", reporterType, count);
        for (int i = 0; i < count; ++i) {
            final String rawTypeName = readAnnotation(di);
            final Class<? extends Annotation> type = annotations.get(rawTypeName);
            if (type == null) {
                continue;
            }
            final String externalTypeName = typeName.replace('/', '.');
            switch (reporterType) {
                case 'T':
                    typeReporter.reportTypeAnnotation(type, externalTypeName);
                    break;
                case 'F':
                    fieldReporter.reportFieldAnnotation(type, externalTypeName, memberName);
                    break;
                case 'M':
                    methodReporter.reportMethodAnnotation(type, externalTypeName, memberName);
                    break;
                default:
                    throw new AssertionError("reporterType=" + reporterType);
            }
        }
    }

    private String readAnnotation(final DataInput di) throws IOException {
        final String rawTypeName = resolveUtf8(di);
        // num_element_value_pairs
        final int count = di.readUnsignedShort();
        print("annotation elements count: %d", count);
        for (int i = 0; i < count; ++i) {
            if (logger.isDebugEnabled()) {
                print("element '%s'", resolveUtf8(di));
            } else {
                di.skipBytes(2);
            }
            readAnnotationElementValue(di);
        }
        return rawTypeName;
    }

    private void readAnnotationElementValue(final DataInput di) throws IOException {
        final int tag = di.readUnsignedByte();
        print("tag='%c'", (char) tag);
        switch (tag) {
            case BYTE:
            case CHAR:
            case DOUBLE:
            case FLOAT:
            case INT:
            case LONG:
            case SHORT:
            case BOOLEAN:
            case STRING:
                di.skipBytes(2);
                break;
            case ENUM:
                di.skipBytes(4); // 2 * u2
                break;
            case CLASS:
                di.skipBytes(2);
                break;
            case ANNOTATION:
                readAnnotation(di);
                break;
            case ARRAY:
                final int count = di.readUnsignedShort();
                for (int i = 0; i < count; ++i) {
                    readAnnotationElementValue(di);
                }
                break;
            default:
                throw new ClassFormatError("Not a valid annotation element type tag: 0x" +
                        Integer.toHexString(tag));
        }
    }

    /**
     * Look up the String value, identified by the u2 index value from constant pool
     * (direct or indirect).
     */
    private String resolveUtf8(final DataInput di) throws IOException {
        final int index = di.readUnsignedShort();
        final Object value = constantPool[index];
        final String s;
        if (value instanceof Integer) {
            s = (String) constantPool[(Integer) value];
            print("resolveUtf8(%d): %d --> %s", index, value, s);
        } else {
            s = (String) value;
            print("resolveUtf8(%d): %s", index, s);
        }

        return s;
    }

    /**
     * Helper method for simple (debug) logging.
     */
    private static void print(final String message, final Object... args) {
        if (logger.isDebugEnabled()) {
            final String logMessage;
            if (args.length == 0) {
                logMessage = message;
            } else {
                for (int i = 0; i < args.length; ++i) {
                    // arguments may be null
                    if (args[i] == null) {
                        continue;
                    }
                    if (args[i].getClass().isArray()) {
                        // cast back to array! Note that primitive arrays are not supported
                        args[i] = Arrays.toString((Object[]) args[i]);
                    } else if (args[i] == Class.class) {
                        args[i] = ((Class<?>) args[i]).getName();
                    }
                }
                logMessage = String.format(message, args);
            }
            logger.trace(logMessage); // SUPPRESS CHECKSTYLE RegexpSinglelineJavaCheck
        }
    }

    /**
     * Reclaim memory.
     */
    public void destroy() {
        annotations.clear();
        constantPool = null;
        cpBuffer.destroy();
    }

}
