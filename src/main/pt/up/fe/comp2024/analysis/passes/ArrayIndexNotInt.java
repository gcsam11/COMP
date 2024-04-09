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

/**
 * Checks if the index of the array access is an int.
 */
public class ArrayIndexNotInt extends AnalysisVisitor {

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

        var arrayIdExpr = arrayAccessOp.getChild(1);

        Type type = TypeUtils.getExprType(arrayIdExpr, table, currentMethod);

        if (type.getName().equals(TypeUtils.getIntTypeName())){
            return null;
        }

        // Create error report
        var message = String.format("'%s' array access index is not an INT.", arrayAccessOp.getChild(0).get("ID"));
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(arrayAccessOp),
                NodeUtils.getColumn(arrayAccessOp),
                message,
                null)
        );

        return null;
    }
}