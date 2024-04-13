package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;
import java.util.Objects;

/**
 * Checks if a member access is correct, all the imported classes logistic.
 */
public class methodParams extends AnalysisVisitor {

    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.MEMBER_ACCESS_OP, this::visitMemberAccessOp);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");

        // Check if varargs is a parameter and if it is the last parameter
        var parameters = method.getChildren(Kind.PARAM_DECL);
        if(parameters.stream().anyMatch(param -> param.getChild(0).hasAttribute("isVarargs"))){
            var varargs = parameters.stream().filter(param -> param.getChild(0).hasAttribute("isVarargs")).findFirst().get();
            if(parameters.indexOf(varargs) != parameters.size()-1){
                var message = String.format("Varargs is not the last parameter in '%s'", currentMethod);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(method),
                        NodeUtils.getColumn(method),
                        message,
                        null)
                );
            }
        }

        return null;
    }

    private Void visitMemberAccessOp(JmmNode memberAccess, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        return null;
    }

    private Boolean checkParameterTypes(JmmNode memberAccess, String methodName, SymbolTable table) {
        return false;
    }

}

