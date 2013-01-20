package me.trnl.javadoc;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.updater.*;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.aether.version.InvalidVersionSpecificationException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class RepositoryIndexer {

    private final PlexusContainer plexusContainer;

    private final Indexer indexer;

    private final IndexUpdater indexUpdater;

    private final Wagon httpWagon;

    public RepositoryIndexer() throws PlexusContainerException, ComponentLookupException {
        this.plexusContainer = new DefaultPlexusContainer();

        // lookup the indexer components from plexus
        this.indexer = plexusContainer.lookup(Indexer.class);
        this.indexUpdater = plexusContainer.lookup(IndexUpdater.class);
        // lookup wagon used to remotely fetch index
        this.httpWagon = plexusContainer.lookup(Wagon.class, "http");

    }

    public void perform(int i) throws IOException, ComponentLookupException, InvalidVersionSpecificationException {
        IndexingContext centralContext = createIndexingContext();

        updateIndex(centralContext);

        listIndex(centralContext, i);
    }

    private void listIndex(IndexingContext centralContext, int size) throws IOException {

        System.out.println();
        System.out.println("Using index");
        System.out.println("===========");
        System.out.println();


        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("ec2-23-23-101-61.compute-1.amazonaws.com");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.queueDeclare("javadoc", true, false, false, null);


        final IndexSearcher searcher = centralContext.acquireIndexSearcher();

        try {
            final IndexReader ir = searcher.getIndexReader();
            for (int i = 100000; i < size; i++) {
                if (!ir.isDeleted(i)) {
                    final Document doc = ir.document(i);
                    final ArtifactInfo ai = IndexUtils.constructArtifactInfo(doc, centralContext);
                    String gav = ai.groupId + ":" + ai.artifactId + ":" + ai.version;
                    channel.basicPublish("", "javadoc", null, gav.getBytes());
                    System.out.println(" [x] Sent '" + gav + "'");
                }
            }
        } finally {
            centralContext.releaseIndexSearcher(searcher);
            channel.close();
            connection.close();
        }
    }

    private IndexingContext createIndexingContext() throws ComponentLookupException, IOException {
        // Files where local cache is (if any) and Lucene Index should be located
        File centralLocalCache = new File("target/central-cache");
        File centralIndexDir = new File("target/central-index");

        // Creators we want to use (search for fields it defines)
        List<IndexCreator> indexers = new ArrayList<IndexCreator>();
        indexers.add(plexusContainer.lookup(IndexCreator.class, "min"));
        indexers.add(plexusContainer.lookup(IndexCreator.class, "jarContent"));
        indexers.add(plexusContainer.lookup(IndexCreator.class, "maven-plugin"));

        // Create context for central repository index
        return indexer.createIndexingContext("central-context", "central", centralLocalCache, centralIndexDir,
                "http://repo1.maven.org/maven2", null, true, true, indexers);
    }

    private void updateIndex(IndexingContext centralContext) throws IOException {
        System.out.println("Updating Index...");
        System.out.println("This might take a while on first run, so please be patient!");
        TransferListener listener = new AbstractTransferListener() {
            public void transferStarted(TransferEvent transferEvent) {
                System.out.print("  Downloading " + transferEvent.getResource().getName());
            }

            public void transferProgress(TransferEvent transferEvent, byte[] buffer, int length) {
            }

            public void transferCompleted(TransferEvent transferEvent) {
                System.out.println(" - Done");
            }
        };
        ResourceFetcher resourceFetcher = new WagonHelper.WagonFetcher(httpWagon, listener, null, null);

        Date centralContextCurrentTimestamp = centralContext.getTimestamp();
        IndexUpdateRequest updateRequest = new IndexUpdateRequest(centralContext, resourceFetcher);
        IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex(updateRequest);
        if (updateResult.isFullUpdate()) {
            System.out.println("Full update happened!");
        } else if (updateResult.getTimestamp().equals(centralContextCurrentTimestamp)) {
            System.out.println("No update needed, index is up to date!");
        } else {
            System.out.println("Incremental update happened, change covered " + centralContextCurrentTimestamp
                    + " - " + updateResult.getTimestamp() + " period.");
        }

        System.out.println();
    }
}
