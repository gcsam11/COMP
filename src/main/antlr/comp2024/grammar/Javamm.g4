grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
MUL : '*' ;
ADD : '+' ;
SUB : '-';
DIV : '/';
DOT : '.';
ELLIPSIS : '...';
LSQUARE : '[';
RSQUARE : ']';
COMMA : ',';
AND : '&&';
LT : '<';
NOT : '!';

CLASS : 'class' ;
INT : 'int' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
TRUE : 'true' ;
FALSE : 'false' ;
THIS : 'this' ;
NULL : 'null' ;
IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;
NEW : 'new' ;
BOOLEAN : 'boolean' ;
STATIC : 'static' ;
VOID : 'void' ;
MAIN : 'main' ;
STRING : 'String' ;
IMPORT : 'import';
EXTENDS : 'extends';
LENGTH : 'length';

INTEGER : '0' | [1-9][0-9]* ;
ID : [a-zA-Z_$] [a-zA-Z0-9_$]*;

ML_COMMENT : '/*' .*? '*/' -> skip ;
EOL_COMMENT : '//' ~[\r\n]* -> skip ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDeclaration)* classDeclaration EOF
    ;

importDeclaration
    : IMPORT ID ( DOT ID )* SEMI
    ;

classDeclaration
    : CLASS ID ( EXTENDS ID )? LCURLY ( varDeclaration )* ( methodDeclaration )* RCURLY
    ;

varDeclaration
    : type ID SEMI
    ;

methodDeclaration
    : (PUBLIC)? type ID LPAREN ( type ID ( COMMA type ID )* )? RPAREN LCURLY ( varDeclaration)* ( statement )* RETURN expression SEMI RCURLY
    | (PUBLIC)? STATIC VOID MAIN LPAREN STRING LSQUARE RSQUARE ID RPAREN LCURLY ( varDeclaration )* ( statement )* RCURLY
    ;

type
    : INT LSQUARE RSQUARE
    | INT ELLIPSIS
    | BOOLEAN
    | INT
    | ID
    | anIntArray
    ;

statement
    : LCURLY ( statement )* RCURLY
    | IF LPAREN expression RPAREN statement ELSE statement
    | WHILE LPAREN expression RPAREN statement
    | expression SEMI
    | ID EQUALS expression SEMI
    | ID LSQUARE expression RSQUARE EQUALS expression SEMI
    ;

expression
    : expression (DOT LENGTH | DOT ID LPAREN ( expression ( COMMA expression )* )? RPAREN | LPAREN expression RPAREN | expression LSQUARE expression RSQUARE)
    | expression (NOT) expression
    | (NEW INT LSQUARE expression RSQUARE | NEW ID LPAREN RPAREN)
    | expression (MUL | DIV) expression
    | expression (ADD | SUB) expression
    | expression (LT) expression
    | expression (AND) expression
    | LSQUARE ( expression ( COMMA expression )* )? RSQUARE
    | INT
    | TRUE
    | FALSE
    | ID
    | THIS
    | NULL
    | INTEGER
    ;

anIntArray
    : INT LSQUARE ( INTEGER ( COMMA INTEGER )* )? RSQUARE
    ;
