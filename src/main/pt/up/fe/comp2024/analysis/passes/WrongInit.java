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
 * Checks if an assignment is correct.
 */
public class WrongInit extends AnalysisVisitor {

    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitAssignStmt(JmmNode arrayAssignStmt, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        try {
            Type idType = TypeUtils.getExprType(arrayAssignStmt.getChild(0), table, currentMethod);
            Type assignType = TypeUtils.getExprType(arrayAssignStmt.getChild(1), table, currentMethod);

            System.out.println(assignType);

            if(!idType.isArray()){
                if(assignType.isArray()){
                    var message = String.format("Assigning array to non array variable '%s'", arrayAssignStmt.getChild(0).get("value"));
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(arrayAssignStmt),
                            NodeUtils.getColumn(arrayAssignStmt),
                            message,
                            null)
                    );

                    return null;
                }
                if(idType.getName().equals(assignType.getName())) {
                    return null;
                }
                var message = String.format("Assigning '%s' to '%s'.", assignType.getName(), idType.getName());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(arrayAssignStmt),
                        NodeUtils.getColumn(arrayAssignStmt),
                        message,
                        null)
                );
            }
            else{
                if(!assignType.isArray()){
                    var message = String.format("Assigning non array to array variable '%s'", arrayAssignStmt.getChild(0).get("value"));
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(arrayAssignStmt),
                            NodeUtils.getColumn(arrayAssignStmt),
                            message,
                            null)
                    );
                }
                if(idType.getName().equals(assignType.getName())) {
                    return null;
                }
                var message = String.format("Assigning '%s' to '%s'.", assignType.getName(), idType.getName());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(arrayAssignStmt),
                        NodeUtils.getColumn(arrayAssignStmt),
                        message,
                        null)
                );
            }
        } catch (RuntimeException e) {
            var message = String.format("'%s'", e.getMessage());
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayAssignStmt),
                    NodeUtils.getColumn(arrayAssignStmt),
                    message,
                    null)
            );
        }

        return null;
    }
}