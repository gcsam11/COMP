package pt.up.fe.comp2024.optimization_jasmin;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.Map;
import java.util.Objects;

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
        addVisit("NewOpArray", this::visitNewOpArray);
        addVisit("LengthOp", this::visitLengthOp);
    }

    private Void visitIntegerLiteral(JmmNode integerLiteral, StringBuilder code) {
        var value = Integer.parseInt(integerLiteral.get("value"));
        if(value >= -1 && value <= 5){
            code.append("iconst_").append(value).append(NL);
        }
        else if(value >= -127 && value <= 128){
            code.append("bipush ").append(value).append(NL);
        }
        else{
            code.append("ldc ").append(value).append(NL);
        }
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
        var type = TypeUtils.getExprType(idExpr, table, currentMethod);
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
        if (table.getParameters(currentMethod).stream().anyMatch(parameter -> parameter.getName().equals(name)) ||
                table.getLocalVariables(currentMethod).stream().anyMatch(variable -> variable.getName().equals(name))) {
            isField = false;
        }

        if(idExpr.getParent().getKind().equals("MemberAccessOp")) {
            return null;
        }


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
            code.append("istore_").append(identifierBeingAssigned).append(NL);
            code.append("iload_").append(identifierBeingAssigned);
        } else {
            if(code != null)
                if(TypeUtils.checkIfTypeIsPrimitive(type) && !type.isArray())
                    code.append("iload_").append(reg).append(NL);
                else
                    code.append("aload_").append(reg).append(NL);
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
        if (!binaryExpr.getParent().getKind().equals("ParenOp")  && !binaryExpr.getParent().getKind().equals("BinaryExpr") && !binaryExpr.getParent().getKind().equals("ReturnStmt")) {
            code.append("ldc 0").append(NL);
            code.append("istore_").append(currentRegisters.get(binaryExpr.getParent().getChild(0).get("value"))).append(NL);
        }

        code.append(op).append(NL);


        if (!binaryExpr.getParent().getKind().equals("ParenOp") && !binaryExpr.getParent().getKind().equals("BinaryExpr") && !binaryExpr.getParent().getKind().equals("ReturnStmt")) {
            // store and load in next register
            var name = binaryExpr.getParent().getChild(0).get("value");
            var reg = currentRegisters.get(name);

            // If no mapping, variable has not been assigned yet, create mapping
            if (reg == null) {
                reg = currentRegisters.size();
                currentRegisters.put(name, reg);
            }

            code.append("istore_").append(reg).append(NL);
            code.append("iload_").append(reg).append(NL);
        }

        return null;
    }


    private Void visitNewOpObject(JmmNode newOp, StringBuilder code) {
        var name = "";
        if(!newOp.getParent().getKind().equals("ParenOp")){
            name = newOp.getParent().getChild(0).get("value");
        }
        var reg = currentRegisters.get(name);

        // If no mapping, variable has not been assigned yet, create mapping
        if (reg == null) {
            reg = currentRegisters.size();
            currentRegisters.put(name, reg);
        }

        var program = newOp.getParent();

        while(!program.getKind().equals("Program")) {
            program = program.getParent();
        }

        boolean isImport = false;
        var importName = "";

        for(var child: program.getChildren(Kind.IMPORT_DECL)){
            var iName = child.get("importName");
            var importName2 = iName.replace("[", "").replace("]", "").replace(" ", "");
            var importNameList = importName2.split(",");
            for(var importNameElement: importNameList){
                if(importNameElement.equals(newOp.get("value"))){
                    isImport = true;
                    importName = iName;
                }
            }
        }

        if(isImport){
            var classes = importName
                    .replace("[", "")
                    .replace("]", "")
                    .replace(" ", "")
                    .replace(",","/");
            code.append("new ").append(classes).append(NL);
            code.append("dup" + NL);
            code.append("invokespecial ").append(classes).append("/<init>()V").append(NL);
            return null;
        }
        else{
            code.append("new ").append(newOp.get("value")).append(NL);
            code.append("dup" + NL);
            code.append("astore_").append(reg).append(NL);
            code.append("aload_").append(reg).append(NL);
            code.append("invokespecial ").append(newOp.get("value")).append("/<init>()V").append(NL);
            return null;
        }
    }

    private Void visitMemberAccessOp(JmmNode memberAccessOp, StringBuilder code) {
        var funcName = memberAccessOp.get("func");
        var memberAccessType = TypeUtils.getExprType(memberAccessOp, table, currentMethod);
        boolean isPrimitive = TypeUtils.checkIfTypeIsPrimitive(memberAccessType);
        // get register for object
        var firstChild = memberAccessOp.getChild(0);
        if(memberAccessOp.getChild(0).getKind().equals("ParenOp")){
            for(var child: memberAccessOp.getChild(0).getDescendants()){
                if(!child.getKind().equals("ParenOp")){
                    firstChild = child;
                }
            }
        }
        var name = firstChild.get("value");
        var reg = currentRegisters.get(name);

        // If no mapping, variable has not been assigned yet, create mapping
        if (reg == null) {
            reg = currentRegisters.size();
            currentRegisters.put(name, reg);
        }
        // aload object if it is a class method and not directly from import
        if(!firstChild.getKind().equals("This") && !table.getImports().contains(firstChild.get("value"))){
            code.append("aload_").append(reg).append(NL);
        }
        // load parameters
        for(var child : memberAccessOp.getChildren().subList(1, memberAccessOp.getNumChildren())) {
            if (child.getKind().equals("ParenOp")) {
                for (var grandChild : child.getDescendants()) {
                    if (!grandChild.getKind().equals("ParenOp")) {
                        child = grandChild;
                    }
                }
            }

            if (child.getKind().equals("BinaryExpr")) {
                continue;
            }

            var idType = TypeUtils.getExprType(child, table, currentMethod);
            String childName;

            if(child.getKind().equals("LengthOp")) {
                childName = child.getChild(0).get("value");
            } else if (!child.getKind().equals("MemberAccessOp")) {
                childName = child.get("value");
            } else {
                childName = child.get("func");
            }

            boolean isField = false;
            String fieldType = "";
            for(var field : table.getFields()) {
                if (field.getName().equals(childName)) {
                    isField = true;
                    fieldType = field.getType().getName();
                    break;
                }
            }
            if (table.getParameters(currentMethod).stream().anyMatch(parameter -> parameter.getName().equals(childName)) ||
                    table.getLocalVariables(currentMethod).stream().anyMatch(variable -> variable.getName().equals(childName))) {
                isField = false;
            }

            if (isField) {
                if(fieldType.equals("int"))
                    fieldType = "I";
                else if(fieldType.equals("boolean"))
                    fieldType = "Z";
                code.append("aload_0").append(NL);
                code.append("getfield ").append(table.getClassName()).append("/").append(childName).append(" ").append(fieldType).append(NL);
                reg = currentRegisters.get(childName);

                // If no mapping, variable has not been assigned yet, create mapping
                if (reg == null) {
                    reg = currentRegisters.size();
                    currentRegisters.put(childName, reg);
                }
                code.append("istore_").append(reg).append(NL);
                code.append("iload_").append(reg).append(NL);
            } else {

                reg = currentRegisters.get(childName);

                // If no mapping, variable has not been assigned yet, create mapping
                if (reg == null) {
                    reg = currentRegisters.size();
                    currentRegisters.put(childName, reg);
                }

                if (!child.getKind().equals("IntegerLiteral") && !child.getKind().equals("BooleanLiteral")) {
                    if (idType.getName().equals(TypeUtils.getIntTypeName()) && !idType.isArray()) {
                        code.append("iload_").append(currentRegisters.get(childName)).append(NL);
                    } else if (idType.getName().equals(TypeUtils.getBooleanTypeName())) {
                        code.append("iload_").append(currentRegisters.get(childName)).append(NL);
                    } else {
                        code.append("aload_").append(currentRegisters.get(childName)).append(NL);
                    }
                }
            }
        }

        if(firstChild.getKind().equals("This")){
            code.append("invokevirtual ");
        }
        else if(table.getImports().contains(memberAccessType.getName()) || table.getMethods().contains(memberAccessType.getName())){
            code.append("invokestatic ");
        }
        else{
            code.append("invokevirtual ");
        }

        if(isPrimitive){
            code.append(table.getClassName()).append("/");
        }
        else{
            var importNode = memberAccessOp.getParent();

            while(!importNode.getKind().equals("Program")) {
                importNode = importNode.getParent();
            }

            var importNodes = importNode.getChildren();
            for(var anImport: importNodes) {
                var classes = anImport.get("importName")
                        .replace("[", "")
                        .replace("]", "")
                        .replace(" ", "")
                        .replace(",","/");

                var auxLast = classes.split("/");
                System.out.println(classes);
                if(Objects.equals(auxLast[auxLast.length - 1], memberAccessType.getName())) {
                    code.append(classes).append("/");
                    break;
                }
            }


        }

        code.append(funcName).append("(");

        for(var child : memberAccessOp.getChildren().subList(1, memberAccessOp.getNumChildren())){
            var paramType = TypeUtils.getExprType(child, table, currentMethod);
            if(paramType.getName().equals(TypeUtils.getIntTypeName())){
                code.append("I");
            }
            else if(paramType.getName().equals(TypeUtils.getBooleanTypeName())){
                code.append("Z");
            }
            else if(table.getImports().contains(paramType.getName())){
                var program = memberAccessOp.getParent();
                while(!program.getKind().equals("Program")) {
                    program = program.getParent();
                }
                var classes = "";
                for(var imports: program.getDescendants(Kind.IMPORT_DECL)){
                    if(imports.get("importName").contains(memberAccessType.getName())){
                        classes = imports.get("importName")
                                .replace("[", "")
                                .replace("]", "")
                                .replace(" ", "")
                                .replace(",","/");
                    }
                }
                code.append("L").append(classes).append(";");
            }
        }
        code.append(")");
        switch(memberAccessType.getName()){
            case "Integer", "int":
                code.append("I");
                break;
            case "Boolean", "boolean":
                code.append("Z");
                break;
            case "Void", "void":
                code.append("V");
                break;
            default:
                var program = memberAccessOp.getParent();
                while(!program.getKind().equals("Program")) {
                    program = program.getParent();
                }
                var classes = "";
                for(var imports: program.getDescendants(Kind.IMPORT_DECL)){
                    if(imports.get("importName").contains(memberAccessType.getName())){
                        classes = imports.get("importName")
                                .replace("[", "")
                                .replace("]", "")
                                .replace(" ", "")
                                .replace(",","/");
                    }
                }
                if(table.getImports().contains(name)){
                    code.append("V");
                }
                else{
                    code.append("L").append(classes).append(";");
                }
        }

        boolean isReturn = true;
        if(memberAccessOp.getParent().getKind().equals("ExprStmt")){
            isReturn = false;
        }
        if(isReturn && !memberAccessOp.getAncestor("BinaryExpr").isPresent()){
            code.append(NL);
            // Return store
            if(TypeUtils.checkIfTypeIsPrimitive(TypeUtils.getExprType(memberAccessOp, table, currentMethod)) && !memberAccessOp.getAncestor("BinaryExpr").isPresent()){
                if(memberAccessOp.getAncestor("AssignStmt").isPresent() && !memberAccessOp.getAncestor("MemberAccessOp").isPresent()){
                    name = memberAccessOp.getAncestor("AssignStmt").get().getChild(0).get("value");
                    reg = currentRegisters.get(name);

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (reg == null) {
                        reg = currentRegisters.size();
                        currentRegisters.put(name, reg);
                    }
                    code.append("istore_").append(reg);
                }
                else{
                    var func = memberAccessOp.get("func");
                    reg = currentRegisters.get(func);

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (reg == null) {
                        reg = currentRegisters.size();
                        currentRegisters.put(func, reg);
                    }
                    code.append("istore_").append(reg);
                }
            }
            else{
                if(memberAccessOp.getAncestor("AssignStmt").isPresent() && !memberAccessOp.getAncestor("MemberAccessOp").isPresent()){
                    name = memberAccessOp.getAncestor("AssignStmt").get().getChild(0).get("value");
                    reg = currentRegisters.get(name);

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (reg == null) {
                        reg = currentRegisters.size();
                        currentRegisters.put(name, reg);
                    }
                    code.append("astore_").append(reg);
                }
                else{
                    var func = memberAccessOp.get("func");
                    reg = currentRegisters.get(func);

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (reg == null) {
                        reg = currentRegisters.size();
                        currentRegisters.put(func, reg);
                    }
                    code.append("astore_").append(reg);
                }
            }
        }

        code.append(NL);

        return null;
    }

    private Void visitParenOp(JmmNode parenStmt, StringBuilder code) {
        return null;
    }

    private Void visitThisExpr(JmmNode thisExpr, StringBuilder code) {
        if(thisExpr.getParent().getKind().equals("AssignStmt")){
            code.append("new ").append(table.getClassName()).append(NL);
            code.append("dup").append(NL);
        }
        else{
            code.append("aload_0").append(NL);
        }
        return null;
    }

    private Void visitNewOpArray(JmmNode newOpArray, StringBuilder code) {
        var name = newOpArray.getParent().getChild(0).get("value");
        var reg = currentRegisters.get(name);

        // If no mapping, variable has not been assigned yet, create mapping
        if (reg == null) {
            reg = currentRegisters.size();
            currentRegisters.put(name, reg);
        }

        code.append("newarray int").append(NL);

        return null;
    }

    private Void visitLengthOp(JmmNode lengthOp, StringBuilder code) {
        var name = lengthOp.getParent().getChild(0).get("value");
        var reg = currentRegisters.get(name);

        // If no mapping, variable has not been assigned yet, create mapping
        if (reg == null) {
            reg = currentRegisters.size();
            currentRegisters.put(name, reg);
        }

        code.append("arraylength").append(NL);
        code.append("istore_").append(reg).append(NL);

        return null;
    }

}
