grammar ReactionString;

// Parser rules:

reaction : reactants '->' products ;

reactants : popsum ;
products : popsum ;

popsum :
        popel ('+' popel)*      # PopulationSum
    |   '0'                     # ZeroPopulation
    ;

popel : factor? popname loc? (':' id)?;

factor : NZINT ;

popname : NAME ;

loc : '[' locel (',' locel)* ']' ;

locel :
        NZINT   # ExplicitLocEl
    |   NAME    # VariableLocEl
    ;

id : NZINT ;

// Lexer rules:

NZINT : NZD  D* ;
fragment D : [0-9] ;
fragment NZD : [1-9] ;

NAME : [a-zA-Z][0-9a-zA-Z]* ;

WHITESPACE : [ \t\r\n]+ -> skip ;