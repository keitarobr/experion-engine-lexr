package br.ufsc.ppgcc.experion.extractor.input.engine.lexr;

import br.ufsc.ppgcc.experion.extractor.input.engine.technique.KeygraphExtractionTechnique;

public class KeygraphExtractionTechniqueLexR extends KeygraphExtractionTechnique {

    public KeygraphExtractionTechniqueLexR() {
        super(new KeyGraphLexR());
    }

}
