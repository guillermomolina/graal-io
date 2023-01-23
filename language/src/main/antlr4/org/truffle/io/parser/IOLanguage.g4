/*
 * Copyright (c) 2022, 2023, Guillermo Adrián Molina. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
 
 grammar IOLanguage;

iolanguage: EOL* expression? EOF;

expression: operation (terminator+ operation terminator*)* EOL*;

operation: 
    operation op=('@' | '@@' | '\'' | '.' | '?' | ':') operation
    | operation op='**' operation
    | operation op=('++' | '--') operation
    | operation op=('*' | '/' | '%') operation
    | operation op=('+' | '-') operation
    | operation op=('<<' | '>>') operation
    | operation op=('<' | '>' | '<=' | '>=') operation
    | operation op=('==' | '!=') operation
    | operation op='&' operation
    | operation op='^' operation
    | operation op='|' operation
    | operation op=('and' | '&&') operation
    | operation op=('or' | '||') operation
    | operation op='..' operation
    | operation op=OPERATOR operation
    | assignment
    | sequence
    ;

assignment:
    sequence? name=identifier assign=('+=' | '-=' | '*=' | '/=' | '%=' | '&=' | '^=' | '|=' | '<<=' | '>>=' | '<-' | '<->' | '->') operation
    | sequence? name=identifier assign=('=' | ':=' | '::=') operation
    ;

sequence:
    (literal | literalMessage | subexpression) message*
    | message+
    ;

subexpression: OPEN EOL* expression? CLOSE;

literalMessage: 
    returnMessage
    | breakMessage
    | continueMessage
    | ifMessage
    | whileMessage 
    | forMessage
    | listMessage
    | blockMessage
    ;

message:
    id=SLOT_NAMES
    | GET_SLOT OPEN EOL* (name=STRING|expression) EOL* CLOSE
    | NEW_SLOT OPEN EOL* (name=STRING|expression) COMMA EOL* value=expression CLOSE
    | SET_SLOT OPEN EOL* (name=STRING|expression) COMMA EOL* value=expression CLOSE
    | UPDATE_SLOT OPEN EOL* (name=STRING|expression) COMMA EOL* value=expression CLOSE
    | REPEAT OPEN EOL* expression? EOL* CLOSE
    | DO OPEN EOL* expression? EOL* CLOSE
    | id=IDENTIFIER arguments?
    ;

arguments: OPEN (EOL* expression (COMMA EOL* expression)*)? CLOSE;

returnMessage: RETURN operation?;
breakMessage: BREAK;
continueMessage: CONTINUE;
listMessage: LIST arguments?;
blockMessage: (BLOCK|METHOD) OPEN EOL* parameterList? body=expression? EOL* CLOSE;
parameterList: (identifier EOL* COMMA EOL*)+;
ifMessage:
    ifMessage1 EOL* thenMessage (EOL* elseMessage)?
    | ifThenElseMessage
    ;
ifMessage1: IF OPEN EOL* condition=expression EOL* CLOSE;
thenMessage: THEN OPEN EOL* thenPart=expression EOL* CLOSE;
elseMessage: ELSE OPEN EOL* elsePart=expression EOL* CLOSE;
ifThenElseMessage: 
    IF OPEN EOL* 
        condition=expression EOL* COMMA EOL* 
        thenPart=expression EOL* (COMMA EOL* 
        elsePart=expression EOL*)? 
    CLOSE
    ;
whileMessage: 
    WHILE OPEN EOL* 
        condition=expression EOL* COMMA EOL* 
        body=expression EOL* 
    CLOSE
    ;
forMessage: 
    FOR OPEN EOL* 
        counter=identifier EOL* COMMA EOL* 
        startPart=expression EOL* COMMA EOL* 
        endPart=expression EOL* COMMA EOL* 
        (stepPart=expression EOL* COMMA EOL*)?
        body=expression EOL* 
    CLOSE
    ;
literal: 
    number 
    | str=STRING
    | pseudoVariable
    ;

number:
    decimal
    | FLOAT
    ;

decimal:
    INTEGER 
    | HEXADECIMAL 
    | OCTAL 
    | BINARY
    ;

pseudoVariable: NIL | TRUE | FALSE | SELF | SUPER;

terminator: ';' | EOL;

identifier: 
    IDENTIFIER
    | BLOCK
    | BREAK
    | CONTINUE
    | DO
    | ELSE
    | FALSE
    | FOR
    | GET_SLOT
    | IF
    | LIST
    | METHOD
    | NEW_SLOT
    | NIL
    | REPEAT
    | RETURN
    | SELF
    | SET_SLOT
    | SLOT_NAMES
    | SUPER
    | THEN
    | TRUE
    | UPDATE_SLOT
    | WHILE
    ;

BLOCK: 'block';
BREAK: 'break';
CONTINUE: 'continue';
DO: 'do';
ELSE: 'else';
FALSE: 'false';
FOR: 'for';
GET_SLOT: 'getSlot';
IF: 'if';
LIST: 'list';
METHOD: 'method';
NEW_SLOT: 'newSlot';
NIL: 'nil';
REPEAT: 'repeat';
RETURN: 'return';
SELF: 'self';
SET_SLOT: 'setSlot';
SLOT_NAMES: 'slotNames';
SUPER: 'super';
THEN: 'then';
TRUE: 'true';
UPDATE_SLOT: 'updateSlot';
WHILE: 'while';

LINE_JOINS: '\\' [ \t]* RN -> channel(HIDDEN);
EOL: RN;

WS: [ \t\u000C]+ -> channel(HIDDEN);
COMMENT: '/*' .*? '*/' -> channel(HIDDEN);
LINE_COMMENT: ('//' | '#') ~[\r\n]* -> channel(HIDDEN);

fragment LETTER: [A-Z] | [a-z];
fragment NON_ZERO_DIGIT: [1-9];
fragment DIGIT: [0-9];
fragment HEX_DIGIT: [0-9] | [a-f] | [A-F];
fragment OCT_DIGIT: [0-7];
fragment BIN_DIGIT: [01];
fragment TAB: '\t';
fragment STRING_CHAR: ~('"' | '\r' | '\n');
fragment RN: '\r'? '\n';

fragment EXPONENT_OR_POINT_FLOAT
    : ([0-9]+ | POINT_FLOAT) [eE] [+-]? [0-9]+
    | POINT_FLOAT
    ;

fragment POINT_FLOAT
    : [0-9]* '.' [0-9]+
    | [0-9]+ '.'
    ;

IDENTIFIER: (LETTER | '_') (LETTER | '_' | DIGIT)*;

OPERATOR:  (':' | '.' | '\'' | '~' | '!' | '@' | '$' | 
    '%' | '^' | '&' | '*' | '-' | '+' | '/' | '=' | '{' | '}' | 
    '[' | ']' | '|' | '\\' | '<' | '>' | '?')+
    ;

COMMA: ',';
OPEN: '(' | '[' | '{';
CLOSE:')' | ']' | '}';
STRING: MONOQUOTE | TRIQUOTE;
MONOQUOTE: '"' STRING_CHAR* '"';
TRIQUOTE: '"""' STRING_CHAR* '"""';

INTEGER: [1-9] [0-9]*
                | '0'+
                ;
HEXADECIMAL: '0' [xX] HEX_DIGIT+;
OCTAL: '0' [oO] OCT_DIGIT+;
BINARY: '0' [bB] BIN_DIGIT+;
FLOAT: EXPONENT_OR_POINT_FLOAT;