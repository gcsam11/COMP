package pt.up.fe.comp2024.optimization_jasmin;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class JasminExprGeneratorVisitor extends PostorderJmmVisitor<StringBuilder, Void> {

    private static final String NL = "\n";
    private final SymbolTable table;
    private String currentMethod;
    private HashMap<String, Integer> numberOfArrayAccessCall = new HashMap<>();

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
        addVisit("ArrayAccessOp", this::visitArrayAccessOp);
        addVisit("ArrayCreationOp", this::visitArrayCreationOp);
    }

    public static void loadIStore(int reg, StringBuilder code) {
        if(reg <= 3)
            code.append("istore_").append(reg).append(NL);
        else
            code.append("istore ").append(reg).append(NL);
    }

    public static void loadILoad(int reg, StringBuilder code) {
        if(reg <= 3)
            code.append("iload_").append(reg).append(NL);
        else
            code.append("iload ").append(reg).append(NL);
    }

    public static void loadAStore(int reg, StringBuilder code) {
        if(reg <= 3)
            code.append("astore_").append(reg).append(NL);
        else
            code.append("astore ").append(reg).append(NL);
    }

    public static void loadALoad(int reg, StringBuilder code) {
        if(reg <= 3)
            code.append("aload_").append(reg).append(NL);
        else
            code.append("aload ").append(reg).append(NL);
    }

    private Void checkConstantSize(int size, StringBuilder code) {
        if(size == -1){
            code.append("iconst_m1").append(NL);
        }
        if(size > -1 && size <= 5){
            code.append("iconst_").append(size).append(NL);
        }
        else if(size >= -127 && size <= 128){
            code.append("bipush ").append(size).append(NL);
        }
        else if(size >= -32768 && size <= 32767){
            code.append("sipush ").append(size).append(NL);
        }
        else{
            code.append("ldc ").append(size).append(NL);
        }
        return null;
    }

    private Void visitIntegerLiteral(JmmNode integerLiteral, StringBuilder code) {
        if(integerLiteral.getParent().getKind().equals("ArrayCreationOp")){
            return null;
        }
        var value = Integer.parseInt(integerLiteral.get("value"));
        checkConstantSize(value, code);
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
        if(idExpr.getParent().equals("VarDecl")){
            return null;
        }
        var name = idExpr.get("value");
        var type = TypeUtils.getExprType(idExpr, table, currentMethod);
        Type fieldType = null;
        String fieldTypeString = "";
        boolean isField = false;
        for(var field : table.getFields()) {
            if (field.getName().equals(name)) {
                isField = true;
                fieldType = field.getType();
                break;
            }
        }
        if(table.getImports().stream().anyMatch(importDecl -> importDecl.equals(name))){
            return null;
        }
        if (table.getParameters(currentMethod).stream().anyMatch(parameter -> parameter.getName().equals(name)) ||
                table.getLocalVariables(currentMethod).stream().anyMatch(variable -> variable.getName().equals(name))) {
            isField = false;
        }

        if(idExpr.getParent().getKind().equals("MemberAccessOp")) {
            return null;
        }

        if(isField){
            if(fieldType.getName().equals("int")){
                if(fieldType.isArray()){
                    fieldTypeString = "[I";
                }
                else{
                    fieldTypeString = "I";
                }
            }
            else if(fieldType.getName().equals("boolean"))
                fieldTypeString = "Z";
        }

        var reg = currentRegisters.get(name);
        SpecsCheck.checkNotNull(reg, () -> "No register mapped for variable '" + name + "'");

        if(isField) {
            var identifierBeingAssigned = currentRegisters.get(idExpr.getParent().getChild(0).get("value"));
            code.append("aload_0").append(NL);
            code.append("getfield ").append(table.getClassName()).append("/").append(name).append(" ").append(fieldTypeString).append(NL);
            loadIStore(identifierBeingAssigned, code);
            loadILoad(identifierBeingAssigned, code);
        } else {
            if(code != null)
                if(TypeUtils.checkIfTypeIsPrimitive(type) && !type.isArray())
                    loadILoad(reg, code);
                else
                    loadALoad(reg, code);
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

        code.append(op).append(NL);

        var parent = binaryExpr.getParent();

        while(parent.getKind().equals("ParenOp")){
            parent = parent.getParent();
        }

        switch(parent.getKind()){
            case "BinaryExpr":
                var reg = currentRegisters.size() + 1;
                currentRegisters.put("temp_" + reg, reg);
                loadIStore(reg, code);
                loadILoad(reg, code);
                break;
            case "AssignStmt":
                var name = binaryExpr.getParent().getChild(0).get("value"); // TODO - Could be array access
                var regAssign = currentRegisters.get(name);

                // If no mapping, variable has not been assigned yet, create mapping
                if (regAssign == null) {
                    regAssign = currentRegisters.size();
                    currentRegisters.put(name, regAssign);
                }
                if(currentRegisters.containsKey("temp_" + regAssign)){
                    regAssign = currentRegisters.size();

                    currentRegisters.remove(name);
                    currentRegisters.put(name, regAssign);
                }
                System.out.println(name + ' ' + regAssign);
                loadIStore(regAssign, code);
                break;
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
            loadAStore(reg, code);
            loadALoad(reg, code);
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
        if (reg == null && !name.equals("this")) {
            reg = currentRegisters.size();
            currentRegisters.put(name, reg);
        }
        // aload object if it is a class method and not directly from import
        if(!firstChild.getKind().equals("This") && !table.getImports().contains(firstChild.get("value"))){
            loadALoad(reg, code);
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

            var idType = TypeUtils.getExprType(child, table, currentMethod);
            String childName;

            if(child.getKind().equals("LengthOp")) {
                childName = child.getChild(0).get("value") + ".length";
            } else if (child.getKind().equals("ArrayAccessOp")) {
                if (numberOfArrayAccessCall.containsKey(child.getChild(0).get("value"))) {
                    childName = child.getChild(0).get("value") + "[" + numberOfArrayAccessCall.get(child.getChild(0).get("value")) + "]";
                    numberOfArrayAccessCall.replace(child.getChild(0).get("value"), numberOfArrayAccessCall.get(child.getChild(0).get("value")) + 1);
                } else {
                    numberOfArrayAccessCall.put(child.getChild(0).get("value"), 0);
                    childName = child.getChild(0).get("value") + "[" + numberOfArrayAccessCall.get(child.getChild(0).get("value")) + "]";
                    numberOfArrayAccessCall.replace(child.getChild(0).get("value"), numberOfArrayAccessCall.get(child.getChild(0).get("value")) + 1);
                }
            } else if (child.getKind().equals("BinaryExpr")) {
                    reg = currentRegisters.size() + 1;
                    currentRegisters.put("temp_" + reg, reg);
                    loadIStore(reg, code);
                    childName = "temp_" + reg;
            } else if (!child.getKind().equals("MemberAccessOp")) {
                childName = child.get("value");
            } else {
                childName = child.get("func");
            }

            boolean isField = false;
            Type fieldType = null;
            String fieldTypeString = "";
            for(var field : table.getFields()) {
                if (field.getName().equals(childName)) {
                    isField = true;
                    fieldType = field.getType();
                    break;
                }
            }
            if (table.getParameters(currentMethod).stream().anyMatch(parameter -> parameter.getName().equals(childName)) ||
                    table.getLocalVariables(currentMethod).stream().anyMatch(variable -> variable.getName().equals(childName))) {
                isField = false;
            }

            if (isField) {
                if(fieldType.getName().equals("int")) {
                    if (fieldType.isArray()){
                        fieldTypeString = "[I";
                    }
                    else{
                        fieldTypeString = "I";
                    }
                }
                else if(fieldType.getName().equals("boolean"))
                    fieldTypeString = "Z";
                code.append("aload_0").append(NL);
                code.append("getfield ").append(table.getClassName()).append("/").append(childName).append(" ").append(fieldTypeString).append(NL);
                reg = currentRegisters.get(childName);

                // If no mapping, variable has not been assigned yet, create mapping
                if (reg == null) {
                    reg = currentRegisters.size();
                    currentRegisters.put(childName, reg);
                }
                loadIStore(reg, code);
                loadILoad(reg, code);
            } else {
                if (!child.getKind().equals("IntegerLiteral") && !child.getKind().equals("BooleanLiteral")) {

                    reg = currentRegisters.get(childName);

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (reg == null) {
                        reg = currentRegisters.size();
                        currentRegisters.put(childName, reg);
                    }

                    if(child.getKind().equals("ArrayAccessOp")){
                        code.append("iaload").append(NL);
                    } else if (idType.getName().equals(TypeUtils.getIntTypeName()) && !idType.isArray()) {
                        loadILoad(currentRegisters.get(childName), code);
                    } else if (idType.getName().equals(TypeUtils.getBooleanTypeName())) {
                        loadILoad(currentRegisters.get(childName), code);
                    } else {
                        loadALoad(currentRegisters.get(childName), code);
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

            var importNodes = importNode.getChildren("ImportDecl");
            for(var anImport: importNodes) {
                var classes = anImport.get("importName")
                        .replace("[", "")
                        .replace("]", "")
                        .replace(" ", "")
                        .replace(",","/");

                var auxLast = classes.split("/");
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
                if(paramType.isArray()){
                    code.append("[I");
                }
                else{
                    code.append("I");
                }
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
                if(memberAccessType.isArray())
                    code.append("[I");
                else
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
                    loadIStore(reg, code);
                }
                else{
                    var func = memberAccessOp.get("func");
                    reg = currentRegisters.get(func);

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (reg == null) {
                        reg = currentRegisters.size();
                        currentRegisters.put(func, reg);
                    }
                    loadIStore(reg, code);
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
                    loadAStore(reg, code);
                }
                else{
                    var func = memberAccessOp.get("func");
                    reg = currentRegisters.get(func);

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (reg == null) {
                        reg = currentRegisters.size();
                        currentRegisters.put(func, reg);
                    }
                    loadAStore(reg, code);
                }
            }
        }
        else if(isReturn && memberAccessOp.getAncestor("BinaryExpr").isPresent()){
            code.append(NL);
            reg = currentRegisters.size() + 1;
            currentRegisters.put("temp_" + reg, reg);
            loadIStore(reg, code);
            loadILoad(reg, code);
        }
        else{
            code.append(NL);
        }

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
        var name = lengthOp.getChild(0).get("value") + ".length";
        var reg = currentRegisters.get(name);

        // If no mapping, variable has not been assigned yet, create mapping
        if (reg == null) {
            reg = currentRegisters.size();
            currentRegisters.put(name, reg);
        }

        code.append("arraylength").append(NL);
        loadIStore(reg, code);

        return null;
    }

    private Void visitArrayAccessOp(JmmNode arrayAccessOp, StringBuilder code) {
        if(arrayAccessOp.getParent().getKind().equals("ReturnStmt")){
            var reg = currentRegisters.get(arrayAccessOp.getChild(0).get("value") + "[" + arrayAccessOp.getChild(1).get("value") + "]");

            // If no mapping, variable has not been assigned yet, create mapping
            if (reg == null) {
                reg = currentRegisters.size() + 1;
                currentRegisters.put(arrayAccessOp.getChild(0).get("value") + "[" + arrayAccessOp.getChild(1).get("value") + "]", reg);
            }

            code.append("iaload").append(NL);
        }
        return null;
    }

    private Void visitArrayCreationOp(JmmNode arrayCreationOp, StringBuilder code) {
        var variables = arrayCreationOp.getChildren();
        int size = variables.size();

        checkConstantSize(size, code);

        code.append("newarray int").append(NL);

        for(int i = 0; i < variables.size(); i++){
            code.append("dup").append(NL);
            checkConstantSize(i, code);
            checkConstantSize(Integer.parseInt(variables.get(i).get("value")), code);
            code.append("iastore").append(NL);

        }

        var reg = currentRegisters.get(arrayCreationOp.getParent().getChild(0).get("value"));

        // If no mapping, variable has not been assigned yet, create mapping
        if (reg == null) {
            reg = currentRegisters.size();
            currentRegisters.put(arrayCreationOp.getParent().getChild(0).get("value"), reg);
        }

        loadAStore(reg, code);

        return null;
    }
}
