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

import java.util.Objects;

/**
 * Checks if return types are correct.
 */
public class ReturnTypes extends AnalysisVisitor {

    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");

        if(Objects.equals(method.getChild(0).getKind(), Kind.VOID_TYPE.getNodeName())){
            return null;
        }

        if(method.getDescendants(Kind.RETURN_STMT).isEmpty()){
            var message = String.format("Method '%s' does not have a return statement", currentMethod);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(method.getChildren().get(method.getChildren().size() - 1)),
                    NodeUtils.getColumn(method.getChildren().get(method.getChildren().size() - 1)),
                    message,
                    null)
            );
        }

        if(method.getDescendants(Kind.RETURN_STMT).size() > 1){
            var message = String.format("Method '%s' has more than one return statement", currentMethod);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(method.getChildren().get(method.getChildren().size() - 1)),
                    NodeUtils.getColumn(method.getChildren().get(method.getChildren().size() - 1)),
                    message,
                    null)
            );
        }

        if(!method.getChildren().get(method.getChildren().size() - 1).getKind().equals(Kind.RETURN_STMT.getNodeName())){
            var message = String.format("Method '%s' does not end with a return statement", currentMethod);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(method.getChildren().get(method.getChildren().size() - 1)),
                    NodeUtils.getColumn(method.getChildren().get(method.getChildren().size() - 1)),
                    message,
                    null)
            );
        }
        return null;
    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table){
        try{
            var returnType = TypeUtils.getExprType(returnStmt.getChild(0), table, currentMethod);
            var methodReturnType = table.getReturnType(currentMethod);
            if(!Objects.equals(returnType, methodReturnType) && !table.getImports().contains(returnType.getName()) && (!table.getImports().contains(table.getSuper()) && !Objects.equals(returnType.getName(), table.getClassName()))){
                var message = String.format("Return type of '%s' does not match '%s' method's type", returnStmt.getChild(0).getOptional("value").orElse(returnStmt.getChild(0).get("func")), currentMethod);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(returnStmt),
                        NodeUtils.getColumn(returnStmt),
                        message,
                        null)
                );
                return null;
            }
        } catch (RuntimeException e) {
            // Do Nothing
        }

        return null;
    }

}