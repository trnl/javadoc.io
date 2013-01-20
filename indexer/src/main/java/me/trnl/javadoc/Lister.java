package me.trnl.javadoc;

import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.aether.version.InvalidVersionSpecificationException;

import java.io.IOException;

public class Lister {

    public static void main(String... args) throws InterruptedException, PlexusContainerException, ComponentLookupException, IOException, InvalidVersionSpecificationException {
        RepositoryIndexer indexer = new RepositoryIndexer();
        indexer.perform(Integer.parseInt(args[0]));
    }

}
