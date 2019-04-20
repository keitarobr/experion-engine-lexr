package br.ufsc.ppgcc.experion.extractor.input.engine.lexr;

import br.ufsc.ppgcc.experion.extractor.input.engine.technique.KeygraphExtractionTechniqueTFIDF;

public class KeygraphExtractionTechniqueLexRTFIDF extends KeygraphExtractionTechniqueTFIDF {

    public KeygraphExtractionTechniqueLexRTFIDF() {
        super(new KeyGraphLexR());
    }

}
