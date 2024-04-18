package pt.up.fe.comp.initial;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.specs.util.SpecsIo;


public class SemanticAnalysisTest {

    @Test
    public void undeclaredVariable() {
        var result = TestUtils
                .analyse(SpecsIo.getResource("pt/up/fe/comp/initial/semanticanalysis/UndeclaredVariable.jmm"));
        TestUtils.mustFail(result);
        System.out.println(result.getReports());
    }


}
