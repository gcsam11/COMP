package pt.up.fe.comp2024.optimization_jasmin;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.Map;

public class JasminExprGeneratorVisitor extends PostorderJmmVisitor<StringBuilder, Void> {

    private static final String NL = "\n";
    private final SymbolTable table;

    private final Map<String, Integer> currentRegisters;


    public JasminExprGeneratorVisitor(Map<String, Integer> currentRegisters, SymbolTable table) {
        this.table = table;
        this.currentRegisters = currentRegisters;
    }

    @Override
    protected void buildVisitor() {
        // Using strings to avoid compilation problems in projects that
        // might no longer have the equivalent enums in Kind class.
        addVisit("IntegerLiteral", this::visitIntegerLiteral);
        addVisit("BooleanLiteral", this::visitBooleanLiteral);
        addVisit("Identifier", this::visitIdentifier);
        addVisit("BinaryExpr", this::visitBinaryExpr);
        addVisit("NewOpObject", this::visitNewOpObject);
        addVisit("MemberAccessOp", this::visitMemberAccessOpObject);
    }

    private Void visitIntegerLiteral(JmmNode integerLiteral, StringBuilder code) {
        code.append("ldc ").append(integerLiteral.get("value")).append(NL);
        return null;
    }

    private Void visitBooleanLiteral(JmmNode booleanLiteral, StringBuilder code) {
        String value = switch (booleanLiteral.get("value")) {
            case "true" -> "1";
            case "false" -> "0";
            default -> "";
        };
        code.append("iconst_").append(value).append(NL);
        return null;
    }

    private Void visitIdentifier(JmmNode idExpr, StringBuilder code) {
        var name = idExpr.get("value");
        var fieldType = "empty";
        boolean isField = false;
        boolean isFunc = false;
        for(var field : table.getFields()) {
            if (field.getName().equals(name)) {
                isField = true;
                fieldType = field.getType().getName();
                break;
            }
        }

        if(!idExpr.getParent().get("func").isEmpty())
            isFunc = true;

        if(fieldType.equals("int"))
            fieldType = "I";
        else if(fieldType.equals("boolean"))
            fieldType = "Z";

        var reg = currentRegisters.get(name);
        SpecsCheck.checkNotNull(reg, () -> "No register mapped for variable '" + name + "'");

        if(isField) {
            var identifierBeingAssigned = currentRegisters.get(idExpr.getParent().getChild(0).get("value"));
            code.append("aload_0").append(NL);
            code.append("getfield ").append(table.getClassName()).append("/").append(name).append(" ").append(fieldType).append(NL);
            code.append("istore ").append(identifierBeingAssigned);
        }
        else if(isFunc) {
            var funcName = idExpr.getParent().get("func");
            var className = idExpr.getParent().getChild(0).get("value");
            code.append("aload_0").append(NL);
            code.append("invokestatic ").append(className).append("/").append(funcName).append("()V").append(NL);
        } else {
            code.append("iload ").append(reg).append(NL);
        }


        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, StringBuilder code) {

        // since this is a post-order visitor that automatically visits the children
        // we can assume the value for the operation are already loaded in the stack

        //Added this because binary expression does not store the variable in the stack before running the code
        code.append("ldc 0").append(NL);
        code.append("istore ").append(currentRegisters.get(binaryExpr.getParent().getChild(0).get("value"))).append(NL);

        // get the operation
        var op = switch (binaryExpr.get("op")) {
            case "/" -> "idiv";
            case "*" -> "imul";
            case "+" -> "iadd";
            case "-" -> "isub";
            case "<" -> "iflt"; // fazer manualmente, if i < 7 return 1 else return 0
            case "&&" -> "iand";
            default -> throw new NotImplementedException(binaryExpr.get("op"));
        };

        // apply operation
        code.append(op).append(NL);

        return null;
    }


    private Void visitNewOpObject(JmmNode newOp, StringBuilder code) {
        code.append("new ").append(newOp.get("value")).append(NL);
        code.append("dup" + NL);
        code.append("invokespecial ").append(newOp.get("value")).append("/<init>()V").append(NL);
        return null;
    }

    private Void visitMemberAccessOpObject(JmmNode memberAccessOp, StringBuilder code) {
        var funcName = memberAccessOp.get("func");
        var className = memberAccessOp.getChild(0).get("value");
        code.append("invokevirtual ").append(className).append("/").append(funcName).append("()V");
        return null;
    }

}
