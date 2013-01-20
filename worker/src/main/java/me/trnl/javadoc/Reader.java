package me.trnl.javadoc;

import com.mongodb.*;
import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.JavaPackage;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Reader {

    public static final String[] MIRRORS = {
            "http://repo1.maven.org/maven2/"
//            "http://uk.maven.org/maven2",
//            "http://mirrors.ibiblio.org/pub/mirrors/maven2",
//            "http://maven.antelink.com/content/repositories/central/"
    };

    public void perform(String groupId, String artifactId, String version) throws IOException {
        File tmp = new File("output/");
        FileUtils.deleteDirectory(tmp);
        tmp.mkdirs();
        try {
            this.resolve(groupId, artifactId, version, tmp);
            this.parse(groupId, artifactId, version, tmp);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void resolve(String groupId, String artifactId, String version, File destination) throws IOException {

        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("http://repo1.maven.org/maven2/")
                .append(groupId.replaceAll("\\.", "/")).append("/")
                .append(artifactId).append("/")
                .append(version).append("/")
                .append(artifactId).append("-").append(version)
                .append("-sources.jar");

        URL url = new URL(urlBuilder.toString());
        ZipInputStream zin = new ZipInputStream(url.openStream());
        ZipEntry ze;
        while ((ze = zin.getNextEntry()) != null) {
            try {
                File f = new File(destination, ze.getName());

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


    }

    private void parse(String groupId, String artifactId, String version, File output) throws UnknownHostException {
        JavaDocBuilder builder = new JavaDocBuilder();
        builder.addSourceTree(output);

        DB db = new Mongo("23.21.156.235", 27017).getDB("javadoc");
        DBCollection libsCollection = db.getCollection("libs");

        DBObject libObject = new BasicDBObject();
        libObject.put("vr", version); //TODO version
        libObject.put("ga", groupId + ":" + artifactId); //TODO group
        libObject.put("_id",
                new StringBuilder(groupId)
                        .append(":")
                        .append(artifactId)
                        .append(":")
                        .append(version)
                        .toString()
        );
        libsCollection.insert(libObject);


        for (JavaPackage packag : builder.getPackages()) {
            new Writer(db, packag, libObject.get("_id")).run();
        }
    }

}
