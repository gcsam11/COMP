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
 * Checks if the operator type is compatible with both expressions.
 */
public class WrongOpTypes extends AnalysisVisitor {

    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        try{

            Type opType = TypeUtils.getExprType(binaryExpr, table, currentMethod);
            Type leftType = TypeUtils.getExprType(binaryExpr.getChild(0), table, currentMethod);
            Type rightType = TypeUtils.getExprType(binaryExpr.getChild(1), table, currentMethod);

            if(binaryExpr.get("op").equals("<") && leftType.getName().equals(TypeUtils.getIntTypeName()) && rightType.getName().equals(leftType.getName()) && !leftType.isArray() && !rightType.isArray()){
                return null;
            }
            else if(leftType.getName().equals(opType.getName()) && rightType.getName().equals(opType.getName()) && (leftType.isArray() == rightType.isArray())){
                return null;
            }
            else{
                var message = String.format("'%s' and '%s' types in operation '%s' are incompatible.", binaryExpr.getChild(0), binaryExpr.getChild(1), binaryExpr.get("op"));
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(binaryExpr),
                        NodeUtils.getColumn(binaryExpr),
                        message,
                        null)
                );
            }
        }
        catch(RuntimeException e){
            // Do Nothing
        }

        return null;
    }

}