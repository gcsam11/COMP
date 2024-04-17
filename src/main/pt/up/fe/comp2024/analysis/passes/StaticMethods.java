package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

/**
 * Checks if a member access is correct, all the imported classes logistic.
 */
public class StaticMethods extends AnalysisVisitor {

    private String currentMethod;
    private Boolean isStatic;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.THIS, this::visitThis);
        addVisit(Kind.IDENTIFIER, this::visitIdentifier);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        isStatic = Boolean.parseBoolean(method.get("isStatic"));
        return null;
    }

    private Void visitThis(JmmNode thisNode, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        if(isStatic){
            var message = "Cannot access instance members in a static context";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(thisNode),
                    NodeUtils.getColumn(thisNode),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitIdentifier(JmmNode identifier, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        var identifierName = identifier.get("value");

        if (table.getFields().stream()
                .anyMatch(param -> param.getName().equals(identifierName)) && isStatic){
            var message = String.format("Cannot access variable '%s' in static method '%s'", identifierName, currentMethod);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(identifier),
                    NodeUtils.getColumn(identifier),
                    message,
                    null)
            );
        }

        return null;
    }
}