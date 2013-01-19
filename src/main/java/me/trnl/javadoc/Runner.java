package me.trnl.javadoc;

import com.mongodb.*;
import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.*;
import org.bson.types.ObjectId;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Runner {
    public static void main(String[] args) throws URISyntaxException, IOException {

        boolean debug = true;

        JavaDocBuilder builder = new JavaDocBuilder();
        File output = new File("output");
//        output.delete();
//        output.mkdirs();

        URL url = new URL("http://search.maven.org/remotecontent?filepath=org/springframework/spring-core/3.2.0.RELEASE/spring-core-3.2.0.RELEASE-sources.jar");
        ZipInputStream zin = new ZipInputStream(url.openStream());
        ZipEntry ze;
        while ((ze = zin.getNextEntry()) != null && !debug) {
            try {
                File f = new File(output, ze.getName());

                if (ze.isDirectory()) { /* if dir - nothing to do. */
                    f.mkdirs();
                    zin.closeEntry();
                    continue;
                }

                /* Let's start writing file */
                OutputStream baos = new FileOutputStream(f);
                ReadableByteChannel in = Channels.newChannel(zin);
                WritableByteChannel out = Channels.newChannel(baos);
                ByteBuffer buffer = ByteBuffer.allocate(65536);
                while (in.read(buffer) != -1) {
                    buffer.flip();
                    out.write(buffer);
                    buffer.clear();
                }

                zin.closeEntry();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        builder.addSourceTree(output);

        DB db = new Mongo("localhost", 27017).getDB("javadoc");
//        db.dropDatabase();
        DBCollection libsCollection = db.getCollection("libs");

        DBObject libObject = new BasicDBObject();
        libObject.put("_id", new ObjectId()); //TODO _id
        libObject.put("vr", "3.2.0.RELEASE"); //TODO version
        libObject.put("gr", "org.springframework"); //TODO group
        libObject.put("aid", "spring-core"); //TODO artifactId

        for (JavaPackage packag : builder.getPackages()) {
            writePackage(db, packag, libObject.get("_id"));
        }

        libsCollection.insert(libObject);

    }

    private static void writePackage(DB db, JavaPackage packag, Object _libid) {
        DBCollection packagesCollection = db.getCollection("packages");

        DBObject packageObject = new BasicDBObject();
        packageObject.put("_id", new ObjectId());
        packageObject.put("_lib", _libid);

        packageObject.put("n", packag.getName());

        //TODO Annotations

        for (JavaClass javaClass : packag.getClasses()) {
            writeClass(db, javaClass, packageObject.get("_id"), _libid);
        }


        packagesCollection.insert(packageObject);
    }

    private static void writeClass(DB db, JavaClass clazz, Object _pkgid, Object _libid) {
        DBCollection classesCollection = db.getCollection("classes");

        BasicDBObjectBuilder classBuilder = new BasicDBObjectBuilder();
        classBuilder.add("_id", new ObjectId());
        classBuilder.add("_pkg", _pkgid);
        classBuilder.add("_lib", _libid);
        classBuilder.add("comment", clazz.getComment());
        classBuilder.add("n", clazz.getFullyQualifiedName());

        /* Methods */
        List methods = new ArrayList();
        classBuilder.add("methods",methods);
        for (JavaMethod method : clazz.getMethods()) {
            BasicDBObjectBuilder methodBuilder = new BasicDBObjectBuilder();
            methodBuilder.add("name", method.getName());
            methodBuilder.add("comment", method.getComment());
            methodBuilder.add("source", method.getCodeBlock());

             /* Modifiers */
            methodBuilder.add("modifiers", method.getModifiers());


            /* Tags */
            List tags = new ArrayList();
            methodBuilder.add("tags", tags);
            for (DocletTag tag : method.getTags()) {
                BasicDBObjectBuilder tagBuilder = new BasicDBObjectBuilder();
                tagBuilder.add(tag.getName(), tag.getValue());
                tags.add(tagBuilder.get());
            }

            /* Params */
            List params = new ArrayList();
            methodBuilder.add("params", params);
            for (JavaParameter parameter : method.getParameters()) {
                BasicDBObjectBuilder paramBuilder = new BasicDBObjectBuilder();
                paramBuilder.add(parameter.getName(), parameter.getType().getFullyQualifiedName());
                params.add(paramBuilder.get());
            }

            /* Exceptions */
            List exceptions = new ArrayList();
            methodBuilder.add("exceptions", exceptions);
            for (Type exception : method.getExceptions()) {
                exceptions.add(exception.getFullyQualifiedName());
            }

            /* Exceptions */
            List annotations = new ArrayList();
            methodBuilder.add("annotations", annotations);
            for (Annotation annotation : method.getAnnotations()) {
                BasicDBObjectBuilder annotationBuilder = new BasicDBObjectBuilder();
                annotationBuilder.add(annotation.getType().getFullyQualifiedName(), annotation.getNamedParameterMap());
                annotations.add(annotationBuilder.get());
            }

            methods.add(methodBuilder.get());
        }


        classesCollection.insert(classBuilder.get());
    }


}
