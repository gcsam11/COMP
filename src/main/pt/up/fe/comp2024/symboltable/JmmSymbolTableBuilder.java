package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {

        var imports = root.getChildren(Kind.IMPORT_DECL).stream()
                .map(importNode -> importNode.get("importName"))
                .toList();
        var classDecl = root.getChildren(Kind.CLASS_DECL).get(0);
        var superClass = classDecl.getOptional("extension").orElse(null);
        var fields = classDecl.getChildren(Kind.VAR_DECL).stream()
                .map(field -> new Symbol(new Type(field.getChild(0).get("typeName"), Boolean.parseBoolean(field.getChild(0).get("isArray"))), field.get("varName")))
                .toList();
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("className");

        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        return new JmmSymbolTable(className, methods, returnTypes, params, locals, imports, superClass, fields);
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {

        Map<String, Type> map = new HashMap<>();

        classDecl.getChildren(Kind.METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), new Type(method.getChild(0).get("typeName"), Boolean.parseBoolean(method.getChild(0).get("isArray")))));

        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {

        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(Kind.METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), getMethodDeclParams(method)));

        return map;
    }

    public static List<Symbol> getMethodDeclParams(JmmNode methodDecl) {
            return methodDecl.getChildren(Kind.PARAM).stream()
                    .map(param -> new Symbol(new Type(param.getChild(0).get("typeName"), Boolean.parseBoolean(param.getChild(0).get("isArray"))), param.get("var")))
                    .toList();
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {

        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(Kind.METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), getLocalsList(method)));

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl){
        return classDecl.getChildren(Kind.METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {

        return methodDecl.getChildren(Kind.VAR_DECL).stream()
                .map(varDecl -> new Symbol(new Type(varDecl.getChild(0).get("typeName"), Boolean.parseBoolean(varDecl.getChild(0).get("isArray"))), varDecl.get("varName")))
                .toList();
    }

}
