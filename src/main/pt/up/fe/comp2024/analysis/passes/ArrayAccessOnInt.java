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
 * Checks if the accessed array exists.
 */
public class ArrayAccessOnInt extends AnalysisVisitor {

    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ARRAY_ACCESS_OP, this::visitArrayAccessOp);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitArrayAccessOp(JmmNode arrayAccessOp, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        var arrayIdExpr = arrayAccessOp.getChild(0);

        try {

            Type type = TypeUtils.getExprType(arrayIdExpr, table, currentMethod);

            if (type.isArray()) {

                return null;
            }

            // Create error report
            var message = String.format("'%s' is not an array.", arrayIdExpr.get("value"));
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayIdExpr),
                    NodeUtils.getColumn(arrayIdExpr),
                    message,
                    null)
            );
        } catch (Exception e) {
            // Create error report
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayIdExpr),
                    NodeUtils.getColumn(arrayIdExpr),
                    e.getMessage(),
                    null)
            );
        }

        return null;
    }
}