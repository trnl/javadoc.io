package me.trnl.javadoc;

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.JavaSource;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Runner {
    public static void main(String[] args) throws URISyntaxException, IOException {

        boolean debug = false;

        JavaDocBuilder builder = new JavaDocBuilder();
        File output = new File("output");
        output.delete();
        output.mkdirs();

        URL url = new URL("http://search.maven.org/remotecontent?filepath=org/springframework/spring-aop/3.2.0.RELEASE/spring-aop-3.2.0.RELEASE-sources.jar");
        ZipInputStream zin = new ZipInputStream(url.openStream());
        ZipEntry ze;
        while ((ze = zin.getNextEntry()) != null && debug) {
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

        for (JavaSource source : builder.getSources()) {
                System.out.println(source.getPackage());

                for (String s : source.getImports()) {
                    System.out.println(s);
                }
                System.out.println("------------------------------------");

        }

    }
}
