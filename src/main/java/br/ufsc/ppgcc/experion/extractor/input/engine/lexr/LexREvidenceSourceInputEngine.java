package br.ufsc.ppgcc.experion.extractor.input.engine.lexr;

import br.ufsc.ppgcc.experion.Experion;
import br.ufsc.ppgcc.experion.extractor.evidence.PhysicalEvidence;
import br.ufsc.ppgcc.experion.extractor.input.EvidenceSourceInput;
import br.ufsc.ppgcc.experion.extractor.input.engine.technique.ExtractionTechnique;
import br.ufsc.ppgcc.experion.extractor.input.BaseSourceInputEngine;
import br.ufsc.ppgcc.experion.extractor.input.engine.technique.LDAExtractionTechnique;
import br.ufsc.ppgcc.experion.extractor.input.engine.technique.LDAExtractionTechniqueTFIDF;
import br.ufsc.ppgcc.experion.extractor.input.engine.technique.TFIDFExtractionTechnique;
import br.ufsc.ppgcc.experion.model.expert.Expert;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.QueryBuilder;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.optimaize.langdetect.i18n.LdLocale;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.eq;

public class LexREvidenceSourceInputEngine extends BaseSourceInputEngine implements Serializable {

    private String colletionAuthors = "authors";
    private String collectionDocuments = "documents";

    private transient MongoClient connection;
    private transient LdLocale language = LdLocale.fromString("pt");




    public static class LDA extends LexREvidenceSourceInputEngine {
        public LDA() throws SQLException, ClassNotFoundException {
            super(new LDAExtractionTechnique(), true);
        }
    }

    public static class LDATFIDF extends LexREvidenceSourceInputEngine {
        public LDATFIDF() throws SQLException, ClassNotFoundException {
            super(new LDAExtractionTechniqueTFIDF(), true);
        }
    }

    public static class TFIDF extends LexREvidenceSourceInputEngine {
        public TFIDF() throws SQLException, ClassNotFoundException {
            super(new TFIDFExtractionTechnique(), true);
        }
    }

    public static class KeyGraph extends LexREvidenceSourceInputEngine {
        public KeyGraph() throws SQLException, ClassNotFoundException {
            super(new KeygraphExtractionTechniqueLexR(), true);
        }
    }

    public static class KeyGraphTFIDF extends LexREvidenceSourceInputEngine {
        public KeyGraphTFIDF() throws SQLException, ClassNotFoundException {
            super(new KeygraphExtractionTechniqueLexRTFIDF(), true);
        }
    }

    public LexREvidenceSourceInputEngine() throws SQLException, ClassNotFoundException {
        this(null, false);
    }

    public LexREvidenceSourceInputEngine(ExtractionTechnique extractionTechnique, boolean connectToDatabase) {
        setExtractionTechnique(extractionTechnique);
        if (connectToDatabase) {
            try {
                this.connectToDatabase();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public LexREvidenceSourceInputEngine(ExtractionTechnique extractionTechnique) {
        this(extractionTechnique, false);
    }


    public void connectToDatabase() throws SQLException, ClassNotFoundException {
        if (connection == null) {
            connection = new MongoClient(Experion.getInstance().getConfig().getString("lexr.db.host"), Experion.getInstance().getConfig().getInt("lexr.db.port"));
        }
    }

    public void disconnectDatabase() throws SQLException {
        connection.close();
    }

    @Override
    public Set<Expert> getExpertEntities() {
        try {
            this.connectToDatabase();
            Set<Expert> entities = new HashSet<>();
            MongoDatabase db = connection.getDatabase(Experion.getInstance().getConfig().getString("lexr.db.database"));
            MongoCollection col = db.getCollection("authors");

            col.find().forEach(new Consumer() {
                @Override
                public void accept(Object o) {
                    Document doc = (Document) o;
                    Expert expert = new Expert(doc.getString("Id"), doc.getString("Nome"));
                    entities.add(expert);
                }
            });
            return entities;
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<PhysicalEvidence> getNewEvidences(Expert expert, EvidenceSourceInput input) {
        String idInSource = expert.getIdentificationForSource(this.getEvidenceSource());

        try {
            this.connectToDatabase();
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException(e);
        }

        final Set<PhysicalEvidence> evidences = new HashSet<>();

        MongoDatabase db = connection.getDatabase(Experion.getInstance().getConfig().getString("lexr.db.database"));
        MongoCollection col = db.getCollection("documents");
        col.find(eq("authors", idInSource)).forEach(new Consumer() {
            @Override
            public void accept(Object o) {
                Document doc = (Document) o;

                PhysicalEvidence evidence = new PhysicalEvidence();
                evidence.setExpert(expert);

                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.MONTH, 0);
                cal.set(Calendar.YEAR, doc.containsKey("year") ? doc.getInteger("year") : 0);
                evidence.setTimestamp(cal.getTime());

                String keywords = "";
                if (!StringUtils.isBlank(doc.getString("title"))) {
                    keywords += doc.getString("title");
                }

                if (!StringUtils.isBlank(doc.getString("abstract"))) {
                    keywords += " " + doc.getString("abstract");
                }

                keywords = keywords.trim();
                if (!StringUtils.isBlank(keywords)) {
                    evidence.addKeywords(keywords.split(" "));
                    evidence.setInput(input);
                    evidences.add(evidence);
                }

                EvidenceSourceURLLexR url = new EvidenceSourceURLLexR();
                url.setUrl("LEXR Record");
                url.setRetrievedData(String.format("Ano: %d\nTítulo: %s\nAbstract: %s", doc.containsKey("year") ? doc.getInteger("year") : 0, doc.getString("title")
                        , doc.getString("abstract")));
                evidence.setUrl(url);
            }
        });

        return customizeEvidences(expert, evidences);
    }

    protected Set<PhysicalEvidence> customizeEvidences(Expert expert, Set<PhysicalEvidence> evidences) {
        return this.getExtractionTechnique().generateEvidences(expert, evidences, this.getLanguage());
    }


    @Override
    public Set<Expert> findExpertByName(String name) {
        try {
            this.connectToDatabase();
            Set<Expert> entities = new HashSet<>();
            MongoDatabase db = connection.getDatabase(Experion.getInstance().getConfig().getString("lexr.db.database"));
            MongoCollection col = db.getCollection("authors");

            DBObject q = QueryBuilder.start("Nome").is(Pattern.compile(name,
                    Pattern.CASE_INSENSITIVE)).get();


            col.find((Bson) q).forEach(new Consumer() {
                @Override
                public void accept(Object o) {
                    Document doc = (Document) o;
                    Expert expert = new Expert(doc.getString("Id"), doc.getString("Nome"));
                    entities.add(expert);
                }
            });
            return entities;
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
