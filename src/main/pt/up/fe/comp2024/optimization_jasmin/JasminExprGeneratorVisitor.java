package pt.up.fe.comp2024.optimization_jasmin;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.Map;
import java.util.stream.Collectors;

public class JasminExprGeneratorVisitor extends PostorderJmmVisitor<StringBuilder, Void> {

    private static final String NL = "\n";
    private final SymbolTable table;
    private String currentMethod;

    private final Map<String, Integer> currentRegisters;


    public JasminExprGeneratorVisitor(Map<String, Integer> currentRegisters, SymbolTable table, String methodName) {
        this.table = table;
        this.currentRegisters = currentRegisters;
        currentMethod = methodName;
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
        addVisit("MemberAccessOp", this::visitMemberAccessOp);
        addVisit("This", this::visitThisExpr);
        addVisit("ParenOp", this::visitParenOp);
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

        if(idExpr.getParent().getKind().equals("MemberAccessOp"))
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
            return null;
        } else {
            if(code != null)
                code.append("iload ").append(reg).append(NL);
        }


        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, StringBuilder code) {

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

        //Added this because binary expression does not store the variable in the stack before running the code
        if (!binaryExpr.getParent().getKind().equals("ParenOp")) {
            code.append("ldc 0").append(NL);
            code.append("istore ").append(currentRegisters.get(binaryExpr.getParent().getChild(0).get("value"))).append(NL);
        }

        code.append(op).append(NL);


        if (!binaryExpr.getParent().getKind().equals("ParenOp")) {
            // store and load in next register
            var name = binaryExpr.getParent().getChild(0).get("value");
            var reg = currentRegisters.get(name);

            // If no mapping, variable has not been assigned yet, create mapping
            if (reg == null) {
                reg = currentRegisters.size();
                currentRegisters.put(name, reg);
            }

            code.append("istore ").append(reg).append(NL);
            code.append("iload ").append(reg).append(NL);
        }

        return null;
    }


    private Void visitNewOpObject(JmmNode newOp, StringBuilder code) {
        var name = newOp.getParent().getChild(0).get("value");
        var reg = currentRegisters.get(name);

        // If no mapping, variable has not been assigned yet, create mapping
        if (reg == null) {
            reg = currentRegisters.size();
            currentRegisters.put(name, reg);
        }

        code.append("new ").append(newOp.get("value")).append(NL);
        code.append("dup" + NL);
        code.append("astore ").append(reg).append(NL);
        code.append("aload ").append(reg).append(NL);
        code.append("invokespecial ").append(newOp.get("value")).append("/<init>()V").append(NL);
        code.append("pop").append(NL); // remove the reference from the stack
        return null;
    }

    private Void visitMemberAccessOp(JmmNode memberAccessOp, StringBuilder code) {
        var funcName = memberAccessOp.get("func");
        var memberAccessType = TypeUtils.getExprType(memberAccessOp, table, currentMethod);
        boolean isPrimitive = TypeUtils.checkIfTypeIsPrimitive(memberAccessType);
        // get register for object
        var name = memberAccessOp.getChild(0).get("value");
        var reg = currentRegisters.get(name);

        // If no mapping, variable has not been assigned yet, create mapping
        if (reg == null) {
            reg = currentRegisters.size();
            currentRegisters.put(name, reg);
        }
        // aload object if it is a class method
        if(isPrimitive && !memberAccessOp.getChild(0).getKind().equals("This")){
            code.append("aload ").append(reg).append(NL);
        }
        // load parameters
        for(var child : memberAccessOp.getChildren().subList(1, memberAccessOp.getNumChildren())){
            var idType = TypeUtils.getExprType(child, table, currentMethod);
            reg = currentRegisters.get(child.get("value"));

            // If no mapping, variable has not been assigned yet, create mapping
            if (reg == null) {
                reg = currentRegisters.size();
                currentRegisters.put(name, reg);
            }

            if(idType.getName().equals(TypeUtils.getIntTypeName())){
                code.append("iload ").append(currentRegisters.get(child.get("value"))).append(NL);
            }
            else if(idType.getName().equals(TypeUtils.getBooleanTypeName())){
                code.append("iload ").append(currentRegisters.get(child.get("value"))).append(NL);
            }
            else{
                code.append("aload ").append(currentRegisters.get(child.get("value"))).append(NL);
            }
        }
        if(table.getMethods().contains(funcName)){
            code.append("invokevirtual ");
        }
        else{
            code.append("invokestatic ");
        }
        if(isPrimitive){
            code.append(table.getClassName());
        }
        else{
            code.append(memberAccessType.getName());
        }
        code.append("/").append(funcName).append("(");
        for(var child : memberAccessOp.getChildren().subList(1, memberAccessOp.getNumChildren())){
            var idType = TypeUtils.getExprType(child, table, currentMethod);
            if(idType.getName().equals(TypeUtils.getIntTypeName())){
                code.append("I");
            }
            else if(idType.getName().equals(TypeUtils.getBooleanTypeName())){
                code.append("Z");
            }
            else{
                code.append("V");
            }
        }
        code.append(")");
        switch(memberAccessType.getName()){
            case "int":
                code.append("I");
                break;
            case "boolean":
                code.append("Z");
                break;
            default:
                code.append("V");
                break;
        }
        code.append(NL);

        return null;
    }

    private Void visitParenOp(JmmNode parenStmt, StringBuilder code) {
        return null;
    }

    private Void visitThisExpr(JmmNode thisExpr, StringBuilder code) {
        code.append("aload_0").append(NL);
        return null;
    }

}
