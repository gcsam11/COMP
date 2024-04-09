package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

/**
 * Checks if the accessed array exists.
 */
public class ArrayAccessOnInt extends AnalysisVisitor {

    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.RETURN, this::visitReturnExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitReturnExpr(JmmNode varReturnExpress, SymbolTable table) {

        var isReturnTypeArray = varReturnExpress.getChild(0).get("isArray");

        // Create error report
        var message = String.format("Variable '%s' does not exist.", isReturnTypeArray);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(varReturnExpress),
                NodeUtils.getColumn(varReturnExpress),
                message,
                null)
        );

        return null;
    }
}