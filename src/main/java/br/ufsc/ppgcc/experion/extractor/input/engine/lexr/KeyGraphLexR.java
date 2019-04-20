package br.ufsc.ppgcc.experion.extractor.input.engine.lexr;

import br.ufsc.ppgcc.experion.extractor.algorithm.keygraph.Keygraph;

import java.io.InputStream;
import java.net.URL;

public class KeyGraphLexR extends Keygraph {
    @Override
    public InputStream getConfig() {
        return this.getClass().getResourceAsStream("/LEXRConstants.txt");
    }
}
