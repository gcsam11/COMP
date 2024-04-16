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
public class MethodParams extends AnalysisVisitor {

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
        if(parameters.stream().anyMatch(param -> Boolean.parseBoolean(param.getChild(0).get("isVarargs")))){
            var varargs = parameters.stream().filter(param -> Boolean.parseBoolean(param.getChild(0).get("isVarargs"))).findFirst().get();
            if(parameters.indexOf(varargs) != parameters.size()-1){
                var message = String.format("Varargs is not the last parameter in '%s' declaration", currentMethod);
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

        // Compare parameter types with method call types
        var methodName = memberAccess.get("func");

        if(!checkParameterTypes(memberAccess, methodName, table)){
            var message = String.format("Call to method '%s' with wrong parameters", methodName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(memberAccess),
                    NodeUtils.getColumn(memberAccess),
                    message,
                    null)
            );
        }

        return null;
    }

    private Boolean checkParameterTypes(JmmNode memberAccess, String methodName, SymbolTable table) {
        if(!table.getMethods().contains(methodName)){
            return true;
        }
        if(table.getParameters(methodName).stream().anyMatch(type -> type.getType().getName().equals("int..."))){
            return checkParameterTypesVarargs(memberAccess, methodName, table);
        }
        else{ // check 1 by 1
            var methodParams = table.getParameters(methodName);
            var callParams = memberAccess.getChildren().subList(1, memberAccess.getNumChildren());
            if(methodParams.size() != callParams.size()){
                return false;
            }
            for(int i = 0; i < methodParams.size(); i++){
                var methodParam = methodParams.get(i);
                var callParam = callParams.get(i);
                var callParamType = TypeUtils.getExprType(callParam, table, currentMethod);
                if(!Objects.equals(methodParam.getType(), callParamType)){
                    return false;
                }
            }
            return true;

        }
    }

    private Boolean checkParameterTypesVarargs(JmmNode memberAccess, String methodName, SymbolTable table) {
        var methodParams = table.getParameters(methodName);
        var callParams = memberAccess.getChildren().subList(1, memberAccess.getNumChildren());
        if(methodParams.size() > callParams.size()){
            return false;
        }
        for(int i = 0; i < methodParams.size()-1; i++){
            var methodParam = methodParams.get(i);
            var callParam = callParams.get(i);
            var callParamType = TypeUtils.getExprType(callParam, table, currentMethod);
            if(!Objects.equals(methodParam.getName(), callParamType.getName())){
                return false;
            }
        }
        var varargsType = TypeUtils.getIntTypeName();
        for(int i = methodParams.size()-1; i < callParams.size(); i++){
            var callParam = callParams.get(i);
            var callParamType = TypeUtils.getExprType(callParam, table, currentMethod);
            if(!Objects.equals(varargsType, callParamType.getName())){
                return false;
            }
        }
        return true;
    }

}

