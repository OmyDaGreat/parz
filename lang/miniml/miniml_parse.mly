(*
                         CS 51 Final Project
                           MiniML -- Parser

   This grammar is processed by menhir. See
   http://gallium.inria.fr/~fpottier/menhir/manual.html and
   https://dev.realworldocaml.org/parsing-with-ocamllex-and-menhir.html
   for documentation of the various declarations used here.  *)
                  
%{
  open Expr ;;
%}

(* Tokens *)
%token EOF
%token OPEN CLOSE
%token LET ARROW IN REC
%token NEG
%token PLUS MINUS 
%token TIMES
%token LESSTHAN EQUALS
%token IF THEN ELSE 
%token FUNCTION
%token RAISE
%token <string> ID
%token <int> INT 
%token TRUE FALSE

(* Associativity and precedence -- lowest to highest *)
%nonassoc ELSE IN ARROW
%left LESSTHAN EQUALS
%left PLUS MINUS
%left TIMES
%nonassoc NEG
%left INT TRUE FALSE ID RAISE OPEN      (* application -- tightest, left-assoc *)

(* Start symbol of the grammar and its type *)
%start input
%type <Expr.expr> input

(* Grammar rules with actions to build an `expr` value as defined in
   the `Expr` module. *)
%%
input:  exp EOF                         { $1 }

(* All expressions. Application binds tightest (left-assoc via grammar). *)
exp:    exp apparg                      { App($1, $2) }
        | exp PLUS exp                  { Binop(Plus, $1, $3) }
        | exp MINUS exp                 { Binop(Minus, $1, $3) }
        | exp TIMES exp                 { Binop(Times, $1, $3) }
        | exp EQUALS exp                { Binop(Equals, $1, $3) }
        | exp LESSTHAN exp              { Binop(LessThan, $1, $3) }
        | NEG exp                       { Unop(Negate, $2) }
        | IF exp THEN exp ELSE exp      { Conditional($2, $4, $6) }
        | LET ID EQUALS exp IN exp      { Let($2, $4, $6) }
        | LET REC ID EQUALS exp IN exp  { Letrec($3, $5, $7) }
        | FUNCTION ID ARROW exp         { Fun($2, $4) }
        | apparg                        { $1 }

(* Atomic expressions -- the only things that can be unparenthesized
   arguments to a function application. *)
apparg: INT                             { Int $1 }
        | TRUE                          { Bool true }
        | FALSE                         { Bool false }
        | ID                            { Var $1 }
        | RAISE                         { Raise }
        | OPEN exp CLOSE                { $2 }
;

%%
