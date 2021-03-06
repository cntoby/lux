;;  Copyright (c) Eduardo Julian. All rights reserved.
;;  This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
;;  If a copy of the MPL was not distributed with this file,
;;  You can obtain one at http://mozilla.org/MPL/2.0/.

(ns lux.base
  (:require (clojure [template :refer [do-template]]
                     [string :as string])
            [clojure.core.match :as M :refer [matchv]]
            clojure.core.match.array))

;; [Tags]
(def unit-tag (.intern (str (char 0) "unit" (char 0))))

(defn T [elems]
  (case (count elems)
    0
    unit-tag

    1
    (first elems)

    ;; else
    (to-array elems)))

(defmacro defvariant [& names]
  (assert (> (count names) 1))
  `(do ~@(for [[[name num-params] idx] (map vector names (range (count names)))
               :let [last-idx (dec (count names))
                     is-last? (if (= idx last-idx)
                                ""
                                nil)
                     def-name (with-meta (symbol (str "$" name))
                                {::idx idx
                                 ::is-last? is-last?})]]
           (cond (= 0 num-params)
                 `(def ~def-name
                    (to-array [(int ~idx) ~is-last? unit-tag]))

                 (= 1 num-params)
                 `(defn ~def-name [arg#]
                    (to-array [(int ~idx) ~is-last? arg#]))

                 :else
                 (let [g!args (map (fn [_] (gensym "arg"))
                                   (range num-params))]
                   `(defn ~def-name [~@g!args]
                      (to-array [(int ~idx) ~is-last? (T [~@g!args])])))
                 ))))

(defmacro deftuple [names]
  (assert (vector? names))
  `(do ~@(for [[name idx] (map vector names (range (count names)))]
           `(def ~(symbol (str "$" name))
              (int ~idx)))))

;; List
(defvariant
  ("Nil" 0)
  ("Cons" 2))

;; Maybe
(defvariant
  ("None" 0)
  ("Some" 1))

;; Either
(defvariant
  ("Left" 1)
  ("Right" 1))

;; AST
(defvariant
  ("BoolS" 1)
  ("NatS" 1)
  ("IntS" 1)
  ("FracS" 1)
  ("RealS" 1)
  ("CharS" 1)
  ("TextS" 1)
  ("SymbolS" 1)
  ("TagS" 1)
  ("FormS" 1)
  ("TupleS" 1)
  ("RecordS" 1))

;; Type
(defvariant
  ("HostT" 2)
  ("VoidT" 0)
  ("UnitT" 0)
  ("SumT" 2)
  ("ProdT" 2)
  ("LambdaT" 2)
  ("BoundT" 1)
  ("VarT" 1)
  ("ExT" 1)
  ("UnivQ" 2)
  ("ExQ" 2)
  ("AppT" 2)
  ("NamedT" 2))

;; Vars
(defvariant
  ("Local" 1)
  ("Global" 1))

;; Binding
(deftuple
  ["counter"
   "mappings"])

;; Env
(deftuple
  ["name"
   "inner-closures"
   "locals"
   "closure"])

;; ModuleState
(defvariant
  ("Active" 0)
  ("Compiled" 0)
  ("Cached" 0))

;; Host
(deftuple
  ["writer"
   "loader"
   "classes"
   "catching"
   "module-states"
   "type-env"
   "dummy-mappings"
   ])

;; Compiler
(defvariant
  ("Release" 0)
  ("Debug" 0)
  ("Eval" 0)
  ("REPL" 0))

(deftuple
  ["compiler-name"
   "compiler-version"
   "compiler-mode"])

(deftuple
  ["info"
   "source"
   "cursor"
   "modules"
   "scopes"
   "type-vars"
   "expected"
   "seed"
   "scope-type-vars"
   "host"])

;; Compiler
(defvariant
  ("UpperBound" 0)
  ("LowerBound" 0))

(defvariant
  ("GenericTypeVar" 1)
  ("GenericClass" 2)
  ("GenericArray" 1)
  ("GenericWildcard" 1))

;; Privacy Modifiers
(defvariant
  ("DefaultPM" 0)
  ("PublicPM" 0)
  ("PrivatePM" 0)
  ("ProtectedPM" 0))

;; State Modifiers
(defvariant
  ("DefaultSM" 0)
  ("VolatileSM" 0)
  ("FinalSM" 0))

;; Inheritance Modifiers
(defvariant
  ("DefaultIM" 0)
  ("AbstractIM" 0)
  ("FinalIM" 0))

;; Fields
(defvariant
  ("ConstantFieldSyntax" 4)
  ("VariableFieldSyntax" 5))

(defvariant
  ("ConstantFieldAnalysis" 4)
  ("VariableFieldAnalysis" 5))

;; Methods
(defvariant
  ("ConstructorMethodSyntax" 1)
  ("VirtualMethodSyntax" 1)
  ("OverridenMethodSyntax" 1)
  ("StaticMethodSyntax" 1)
  ("AbstractMethodSyntax" 1)
  ("NativeMethodSyntax" 1))

(defvariant
  ("ConstructorMethodAnalysis" 1)
  ("VirtualMethodAnalysis" 1)
  ("OverridenMethodAnalysis" 1)
  ("StaticMethodAnalysis" 1)
  ("AbstractMethodAnalysis" 1)
  ("NativeMethodAnalysis" 1))

;; Meta-data
(defvariant
  ("BoolM" 1)
  ("NatM" 1)
  ("IntM" 1)
  ("FracM" 1)
  ("RealM" 1)
  ("CharM" 1)
  ("TextM" 1)
  ("IdentM" 1)
  ("ListM" 1)
  ("DictM" 1))

;; [Exports]
(def ^:const name-field "_name")
(def ^:const hash-field "_hash")
(def ^:const value-field "_value")
(def ^:const compiler-field "_compiler")
(def ^:const eval-field "_eval")
(def ^:const module-class-name "_")
(def ^:const +name-separator+ ";")

(def ^:const ^String compiler-name "Lux/JVM")
(def ^:const ^String compiler-version "0.6.0")

;; Constructors
(def empty-cursor (T ["" -1 -1]))

(defn get$ [slot ^objects record]
  (aget record slot))

(defn set$ [slot value ^objects record]
  (doto (aclone ^objects record)
    (aset slot value)))

(defmacro update$ [slot f record]
  `(let [record# ~record]
     (set$ ~slot (~f (get$ ~slot record#))
           record#)))

(defn fail* [message]
  ($Left message))

(defn return* [state value]
  ($Right (T [state value])))

(defn transform-pattern [pattern]
  (cond (vector? pattern) (case (count pattern)
                            0
                            unit-tag

                            1
                            (transform-pattern (first pattern))

                            ;; else
                            (mapv transform-pattern pattern))
        (seq? pattern) [(if-let [tag-var (ns-resolve *ns* (first pattern))]
                          (-> tag-var
                              meta
                              ::idx)
                          (assert false (str "Unknown var: " (first pattern))))
                        '_
                        (transform-pattern (vec (rest pattern)))]
        :else pattern
        ))

(defmacro |case [value & branches]
  (assert (= 0 (mod (count branches) 2)))
  (let [value* (if (vector? value)
                 [`(T [~@value])]
                 [value])]
    `(matchv ::M/objects ~value*
       ~@(mapcat (fn [[pattern body]]
                   (list [(transform-pattern pattern)]
                         body))
                 (partition 2 branches)))))

(defmacro |let [bindings body]
  (reduce (fn [inner [left right]]
            `(|case ~right
               ~left
               ~inner))
          body
          (reverse (partition 2 bindings))))

(defmacro |list [& elems]
  (reduce (fn [tail head]
            `($Cons ~head ~tail))
          `$Nil
          (reverse elems)))

(defmacro |table [& elems]
  (reduce (fn [table [k v]]
            `(|put ~k ~v ~table))
          `$Nil
          (reverse (partition 2 elems))))

(defn |get [slot table]
  (|case table
    ($Nil)
    nil
    
    ($Cons [k v] table*)
    (if (.equals ^Object k slot)
      v
      (recur slot table*))))

(defn |put [slot value table]
  (|case table
    ($Nil)
    ($Cons (T [slot value]) $Nil)
    
    ($Cons [k v] table*)
    (if (.equals ^Object k slot)
      ($Cons (T [slot value]) table*)
      ($Cons (T [k v]) (|put slot value table*)))
    ))

(defn |remove [slot table]
  (|case table
    ($Nil)
    table
    
    ($Cons [k v] table*)
    (if (.equals ^Object k slot)
      table*
      ($Cons (T [k v]) (|remove slot table*)))))

(defn |update [k f table]
  (|case table
    ($Nil)
    table

    ($Cons [k* v] table*)
    (if (.equals ^Object k k*)
      ($Cons (T [k* (f v)]) table*)
      ($Cons (T [k* v]) (|update k f table*)))))

(defn |head [xs]
  (|case xs
    ($Nil)
    (assert false (prn-str '|head))

    ($Cons x _)
    x))

(defn |tail [xs]
  (|case xs
    ($Nil)
    (assert false (prn-str '|tail))

    ($Cons _ xs*)
    xs*))

;; [Resources/Monads]
(defn fail [message]
  (fn [_]
    ($Left message)))

(defn return [value]
  (fn [state]
    ($Right (T [state value]))))

(defn bind [m-value step]
  (fn [state]
    (let [inputs (m-value state)]
      (|case inputs
        ($Right ?state ?datum)
        ((step ?datum) ?state)
        
        ($Left _)
        inputs
        ))))

(defmacro |do [steps return]
  (assert (= 0 (rem (count steps) 2)) "The number of steps must be even!")
  (reduce (fn [inner [label computation]]
            (case label
              :let `(|let ~computation ~inner)
              ;; else
              `(bind ~computation
                     (fn [val#]
                       (|case val#
                         ~label
                         ~inner)))))
          return
          (reverse (partition 2 steps))))

;; [Resources/Combinators]
(let [array-class (class (to-array []))]
  (defn adt->text [adt]
    (if (= array-class (class adt))
      (str "[" (->> adt (map adt->text) (interpose " ") (reduce str "")) "]")
      (pr-str adt))))

(defn |++ [xs ys]
  (|case xs
    ($Nil)
    ys

    ($Cons x xs*)
    ($Cons x (|++ xs* ys))))

(defn |map [f xs]
  (|case xs
    ($Nil)
    xs

    ($Cons x xs*)
    ($Cons (f x) (|map f xs*))

    _
    (assert false (prn-str '|map f (adt->text xs)))))

(defn |empty? [xs]
  "(All [a] (-> (List a) Bool))"
  (|case xs
    ($Nil)
    true

    ($Cons _ _)
    false))

(defn |filter [p xs]
  "(All [a] (-> (-> a Bool) (List a) (List a)))"
  (|case xs
    ($Nil)
    xs

    ($Cons x xs*)
    (if (p x)
      ($Cons x (|filter p xs*))
      (|filter p xs*))))

(defn flat-map [f xs]
  "(All [a b] (-> (-> a (List b)) (List a) (List b)))"
  (|case xs
    ($Nil)
    xs

    ($Cons x xs*)
    (|++ (f x) (flat-map f xs*))))

(defn |split-with [p xs]
  (|case xs
    ($Nil)
    (T [xs xs])

    ($Cons x xs*)
    (if (p x)
      (|let [[pre post] (|split-with p xs*)]
        (T [($Cons x pre) post]))
      (T [$Nil xs]))))

(defn |contains? [k table]
  (|case table
    ($Nil)
    false

    ($Cons [k* _] table*)
    (or (.equals ^Object k k*)
        (|contains? k table*))))

(defn |member? [x xs]
  (|case xs
    ($Nil)
    false

    ($Cons x* xs*)
    (or (= x x*) (|member? x xs*))))

(defn fold [f init xs]
  (|case xs
    ($Nil)
    init

    ($Cons x xs*)
    (recur f (f init x) xs*)))

(defn fold% [f init xs]
  (|case xs
    ($Nil)
    (return init)

    ($Cons x xs*)
    (|do [init* (f init x)]
      (fold% f init* xs*))))

(defn folds [f init xs]
  (|case xs
    ($Nil)
    (|list init)

    ($Cons x xs*)
    ($Cons init (folds f (f init x) xs*))))

(defn |length [xs]
  (fold (fn [acc _] (inc acc)) 0 xs))

(defn |range* [from to]
  (if (<= from to)
    ($Cons from (|range* (inc from) to))
    $Nil))

(let [|range* (fn |range* [from to]
                (if (< from to)
                  ($Cons from (|range* (inc from) to))
                  $Nil))]
  (defn |range [n]
    (|range* 0 n)))

(defn |first [pair]
  (|let [[_1 _2] pair]
    _1))

(defn |second [pair]
  (|let [[_1 _2] pair]
    _2))

(defn zip2 [xs ys]
  (|case [xs ys]
    [($Cons x xs*) ($Cons y ys*)]
    ($Cons (T [x y]) (zip2 xs* ys*))

    [_ _]
    $Nil))

(defn |keys [plist]
  (|case plist
    ($Nil)
    $Nil
    
    ($Cons [k v] plist*)
    ($Cons k (|keys plist*))))

(defn |vals [plist]
  (|case plist
    ($Nil)
    $Nil
    
    ($Cons [k v] plist*)
    ($Cons v (|vals plist*))))

(defn |interpose [sep xs]
  (|case xs
    ($Nil)
    xs

    ($Cons _ ($Nil))
    xs
    
    ($Cons x xs*)
    ($Cons x ($Cons sep (|interpose sep xs*)))))

(do-template [<name> <joiner>]
  (defn <name> [f xs]
    (|case xs
      ($Nil)
      (return xs)

      ($Cons x xs*)
      (|do [y (f x)
            ys (<name> f xs*)]
        (return (<joiner> y ys)))))

  map%      $Cons
  flat-map% |++)

(defn list-join [xss]
  (fold |++ $Nil xss))

(defn |as-pairs [xs]
  (|case xs
    ($Cons x ($Cons y xs*))
    ($Cons (T [x y]) (|as-pairs xs*))

    _
    $Nil))

(defn |reverse [xs]
  (fold (fn [tail head]
          ($Cons head tail))
        $Nil
        xs))

(defn add-loc [meta ^String msg]
  (if (.startsWith msg "@")
    msg
    (|let [[file line col] meta]
      (str "@ " file "," line "," col "\n" msg))))

(defn fail-with-loc [msg]
  (fn [state]
    (fail* (add-loc (get$ $cursor state) msg))))

(defn assert! [test message]
  (if test
    (return unit-tag)
    (fail-with-loc message)))

(def get-state
  (fn [state]
    (return* state state)))

(defn try-all% [monads]
  (|case monads
    ($Nil)
    (fail "[Error] There are no alternatives to try!")

    ($Cons m monads*)
    (fn [state]
      (let [output (m state)]
        (|case [output monads*]
          [($Right _) _]
          output

          [_ ($Nil)]
          output
          
          [_ _]
          ((try-all% monads*) state)
          )))
    ))

(defn try-all-% [prefix monads]
  (|case monads
    ($Nil)
    (fail "[Error] There are no alternatives to try!")

    ($Cons m monads*)
    (fn [state]
      (let [output (m state)]
        (|case [output monads*]
          [($Right _) _]
          output

          [_ ($Nil)]
          output

          [($Left ^String error) _]
          (if (.contains error prefix)
            ((try-all-% prefix monads*) state)
            output)
          )))
    ))

(defn exhaust% [step]
  (fn [state]
    (|case (step state)
      ($Right state* _)
      ((exhaust% step) state*)

      ($Left msg)
      (if (.equals "[Reader Error] EOF" msg)
        (return* state unit-tag)
        (fail* msg)))))

(defn ^:private normalize-char [char]
  (case char
    \* "_ASTER_"
    \+ "_PLUS_"
    \- "_DASH_"
    \/ "_SLASH_"
    \\ "_BSLASH_"
    \_ "_UNDERS_"
    \% "_PERCENT_"
    \$ "_DOLLAR_"
    \' "_QUOTE_"
    \` "_BQUOTE_"
    \@ "_AT_"
    \^ "_CARET_"
    \& "_AMPERS_"
    \= "_EQ_"
    \! "_BANG_"
    \? "_QM_"
    \: "_COLON_"
    \. "_PERIOD_"
    \, "_COMMA_"
    \< "_LT_"
    \> "_GT_"
    \~ "_TILDE_"
    \| "_PIPE_"
    ;; default
    char))

(defn normalize-name [ident]
  (reduce str "" (map normalize-char ident)))

(def classes
  (fn [state]
    (return* state (->> state (get$ $host) (get$ $classes)))))

(def +init-bindings+
  (T [;; "lux;counter"
      0
      ;; "lux;mappings"
      (|table)]))

(defn env [name old-name]
  (T [;; "lux;name"
      ($Cons name old-name)
      ;; "lux;inner-closures"
      0
      ;; "lux;locals"
      +init-bindings+
      ;; "lux;closure"
      +init-bindings+]
     ))

(let [define-class (doto (.getDeclaredMethod java.lang.ClassLoader "defineClass" (into-array [String
                                                                                              (class (byte-array []))
                                                                                              Integer/TYPE
                                                                                              Integer/TYPE]))
                     (.setAccessible true))]
  (defn memory-class-loader [store]
    (proxy [java.lang.ClassLoader]
      []
      (findClass [^String class-name]
        (if-let [^bytes bytecode (get @store class-name)]
          (.invoke define-class this (to-array [class-name bytecode (int 0) (int (alength bytecode))]))
          (throw (IllegalStateException. (str "[Class Loader] Unknown class: " class-name))))))))

(def loader
  (fn [state]
    (return* state (->> state (get$ $host) (get$ $loader)))))

(defn host [_]
  (let [store (atom {})]
    (T [;; "lux;writer"
        $None
        ;; "lux;loader"
        (memory-class-loader store)
        ;; "lux;classes"
        store
        ;; "lux;catching"
        $Nil
        ;; "lux;module-states"
        (|table)
        ;; lux;type-env
        (|table)
        ;; lux;dummy-mappings
        (|table)
        ])))

(defn with-no-catches [body]
  "(All [a] (-> (Lux a) (Lux a)))"
  (fn [state]
    (let [old-catching (->> state (get$ $host) (get$ $catching))]
      (|case (body (update$ $host #(set$ $catching $Nil %) state))
        ($Right state* output)
        (return* (update$ $host #(set$ $catching old-catching %) state*) output)

        ($Left msg)
        (fail* msg)))))

(defn default-compiler-info [mode]
  (T [;; compiler-name
      compiler-name
      ;; compiler-version
      compiler-version
      ;; compiler-mode
      mode]
     ))

(defn init-state [mode]
  (T [;; "lux;info"
      (default-compiler-info mode)
      ;; "lux;source"
      $Nil
      ;; "lux;cursor"
      (T ["" -1 -1])
      ;; "lux;modules"
      (|table)
      ;; "lux;scopes"
      $Nil
      ;; "lux;types"
      +init-bindings+
      ;; "lux;expected"
      $None
      ;; "lux;seed"
      0
      ;; scope-type-vars
      $Nil
      ;; "lux;host"
      (host nil)]
     ))

(defn save-module [body]
  (fn [state]
    (|case (body state)
      ($Right state* output)
      (return* (->> state*
                    (set$ $scopes (get$ $scopes state))
                    (set$ $source (get$ $source state)))
               output)

      ($Left msg)
      (fail* msg))))

(defn in-eval? [mode]
  "(-> CompilerMode Bool)"
  (|case mode
    ($Eval) true
    _       false))

(defn in-repl? [mode]
  "(-> CompilerMode Bool)"
  (|case mode
    ($REPL) true
    _       false))

(defn with-eval [body]
  (fn [state]
    (let [old-mode (->> state (get$ $info) (get$ $compiler-mode))]
      (|case (body (update$ $info #(set$ $compiler-mode $Eval %) state))
        ($Right state* output)
        (return* (update$ $info #(set$ $compiler-mode old-mode %) state*) output)

        ($Left msg)
        (fail* msg)))))

(def get-eval
  (fn [state]
    (return* state (->> state (get$ $info) (get$ $compiler-mode) in-eval?))))

(def get-mode
  (fn [state]
    (return* state (->> state (get$ $info) (get$ $compiler-mode)))))

(def get-writer
  (fn [state]
    (let [writer* (->> state (get$ $host) (get$ $writer))]
      (|case writer*
        ($Some datum)
        (return* state datum)

        _
        ((fail-with-loc "[Error] Writer hasn't been set.") state)))))

(def get-top-local-env
  (fn [state]
    (try (let [top (|head (get$ $scopes state))]
           (return* state top))
      (catch Throwable _
        ((fail-with-loc "[Error] No local environment.") state)))))

(def gen-id
  (fn [state]
    (let [seed (get$ $seed state)]
      (return* (set$ $seed (inc seed) state) seed))))

(defn ->seq [xs]
  (|case xs
    ($Nil)
    (list)

    ($Cons x xs*)
    (cons x (->seq xs*))))

(defn ->list [seq]
  (if (empty? seq)
    $Nil
    ($Cons (first seq) (->list (rest seq)))))

(defn |repeat [n x]
  (if (> n 0)
    ($Cons x (|repeat (dec n) x))
    $Nil))

(def get-module-name
  (fn [state]
    (|case (|reverse (get$ $scopes state))
      ($Nil)
      ((fail-with-loc "[Analyser Error] Can't get the module-name without a module.") state)

      ($Cons ?global _)
      (return* state (|head (get$ $name ?global))))))

(defn find-module [name]
  "(-> Text (Lux (Module Compiler)))"
  (fn [state]
    (if-let [module (|get name (get$ $modules state))]
      (return* state module)
      ((fail-with-loc (str "[Error] Unknown module: " name)) state))))

(def get-current-module
  "(Lux (Module Compiler))"
  (|do [module-name get-module-name]
    (find-module module-name)))

(defn with-scope [name body]
  (fn [state]
    (let [old-name (->> state (get$ $scopes) |head (get$ $name))
          output (body (update$ $scopes #($Cons (env name old-name) %) state))]
      (|case output
        ($Right state* datum)
        (return* (update$ $scopes |tail state*) datum)
        
        _
        output))))

(defn run-state [monad state]
  (monad state))

(defn with-closure [body]
  (|do [closure-name (|do [top get-top-local-env]
                       (return (->> top (get$ $inner-closures) str)))]
    (fn [state]
      (let [body* (with-scope closure-name body)]
        (run-state body* (update$ $scopes #($Cons (update$ $inner-closures inc (|head %))
                                                  (|tail %))
                                  state))))))

(let [!out! *out*]
  (defn |log! [& parts]
    (binding [*out* !out!]
      (do (print (apply str parts))
        (flush)))))

(defn |last [xs]
  (|case xs
    ($Cons x ($Nil))
    x

    ($Cons x xs*)
    (|last xs*)

    _
    (assert false (adt->text xs))))

(def get-scope-name
  (fn [state]
    (return* state (->> state (get$ $scopes) |head (get$ $name)))))

(defn without-repl-closure [body]
  (|do [_mode get-mode
        current-scope get-scope-name]
    (fn [state]
      (let [output (body (if (and (in-repl? _mode)
                                  (->> current-scope |last (= "REPL")))
                           (update$ $scopes |tail state)
                           state))]
        (|case output
          ($Right state* datum)
          (return* (set$ $scopes (get$ $scopes state) state*) datum)
          
          _
          output)))))

(defn without-repl [body]
  (|do [_mode get-mode]
    (fn [state]
      (let [output (body (if (in-repl? _mode)
                           (update$ $info #(set$ $compiler-mode $Debug %) state)
                           state))]
        (|case output
          ($Right state* datum)
          (return* (update$ $info #(set$ $compiler-mode _mode %) state*) datum)
          
          _
          output)))))

(defn with-writer [writer body]
  (fn [state]
    (let [old-writer (->> state (get$ $host) (get$ $writer))
          output (body (update$ $host #(set$ $writer ($Some writer) %) state))]
      (|case output
        ($Right ?state ?value)
        (return* (update$ $host #(set$ $writer old-writer %) ?state)
                 ?value)

        _
        output))))

(defn with-expected-type [type body]
  "(All [a] (-> Type (Lux a)))"
  (fn [state]
    (let [output (body (set$ $expected ($Some type) state))]
      (|case output
        ($Right ?state ?value)
        (return* (set$ $expected (get$ $expected state) ?state)
                 ?value)

        _
        output))))

(defn with-cursor [^objects cursor body]
  "(All [a] (-> Cursor (Lux a)))"
  (|let [[_file-name _ _] cursor]
    (if (= "" _file-name)
      body
      (fn [state]
        (let [output (body (set$ $cursor cursor state))]
          (|case output
            ($Right ?state ?value)
            (return* (set$ $cursor (get$ $cursor state) ?state)
                     ?value)

            _
            output))))))

(defn with-analysis-meta [^objects cursor type body]
  "(All [a] (-> Cursor Type (Lux a)))"
  (|let [[_file-name _ _] cursor]
    (if (= "" _file-name)
      (fn [state]
        (let [output (body (->> state
                                (set$ $expected ($Some type))))]
          (|case output
            ($Right ?state ?value)
            (return* (->> ?state
                          (set$ $expected (get$ $expected state)))
                     ?value)

            _
            output)))
      (fn [state]
        (let [output (body (->> state
                                (set$ $cursor cursor)
                                (set$ $expected ($Some type))))]
          (|case output
            ($Right ?state ?value)
            (return* (->> ?state
                          (set$ $cursor (get$ $cursor state))
                          (set$ $expected (get$ $expected state)))
                     ?value)

            _
            output))))))

(def ensure-statement
  "(Lux Unit)"
  (fn [state]
    (|case (get$ $expected state)
      ($None)
      (return* state unit-tag)

      ($Some _)
      ((fail-with-loc "[Error] All statements must be top-level forms.") state))))

(def cursor
  ;; (Lux Cursor)
  (fn [state]
    (return* state (get$ $cursor state))))

(def frac-bits 64)

(let [clean-separators (fn [^String input]
                         (.replaceAll input "_" ""))
      frac-text-to-digits (fn [^String input]
                            (loop [output (vec (repeat frac-bits 0))
                                   index (dec (.length input))]
                              (if (>= index 0)
                                (let [digit (Byte/parseByte (.substring input index (inc index)))]
                                  (recur (assoc output index digit)
                                         (dec index)))
                                output)))
      times5 (fn [index digits]
               (loop [index index
                      carry 0
                      digits digits]
                 (if (>= index 0)
                   (let [raw (->> (get digits index) (* 5) (+ carry))]
                     (recur (dec index)
                            (int (/ raw 10))
                            (assoc digits index (rem raw 10))))
                   digits)))
      frac-digit-power (fn [level]
                         (loop [output (-> (vec (repeat frac-bits 0))
                                           (assoc level 1))
                                times level]
                           (if (>= times 0)
                             (recur (times5 level output)
                                    (dec times))
                             output)))
      frac-digits-lt (fn frac-digits-lt
                       ([subject param index]
                          (and (< index frac-bits)
                               (or (< (get subject index)
                                      (get param index))
                                   (and (= (get subject index)
                                           (get param index))
                                        (frac-digits-lt subject param (inc index))))))
                       ([subject param]
                          (frac-digits-lt subject param 0)))
      frac-digits-sub-once (fn [subject param-digit index]
                             (if (>= (get subject index)
                                     param-digit)
                               (update-in subject [index] #(- % param-digit))
                               (recur (update-in subject [index] #(- 10 (- param-digit %)))
                                      1
                                      (dec index))))
      frac-digits-sub (fn [subject param]
                        (loop [target subject
                               index (dec frac-bits)]
                          (if (>= index 0)
                            (recur (frac-digits-sub-once target (get param index) index)
                                   (dec index))
                            target)))
      frac-digits-to-text (fn [digits]
                            (loop [output ""
                                   index (dec frac-bits)]
                              (if (>= index 0)
                                (recur (-> (get digits index)
                                           (Character/forDigit 10)
                                           (str output))
                                       (dec index))
                                output)))
      add-frac-digit-powers (fn [dl dr]
                              (loop [index (dec frac-bits)
                                     output (vec (repeat frac-bits 0))
                                     carry 0]
                                (if (>= index 0)
                                  (let [raw (+ carry
                                               (get dl index)
                                               (get dr index))]
                                    (recur (dec index)
                                           (assoc output index (rem raw 10))
                                           (int (/ raw 10))))
                                  output)))]
  ;; Based on the LuxRT.encode_frac method
  (defn encode-frac [input]
    (if (= 0 input)
      ".0"
      (loop [index (dec frac-bits)
             output (vec (repeat frac-bits 0))]
        (if (>= index 0)
          (recur (dec index)
                 (if (bit-test input index)
                   (->> (- (dec frac-bits) index)
                        frac-digit-power
                        (add-frac-digit-powers output))
                   output))
          (-> output frac-digits-to-text
              (->> (str "."))
              (.split "0*$")
              (aget 0))))))

  ;; Based on the LuxRT.decode_frac method
  (defn decode-frac [^String input]
    (if (and (.startsWith input ".")
             (< (.length input) (inc frac-bits)))
      (loop [digits-left (-> input
                             (.substring 1)
                             clean-separators
                             frac-text-to-digits)
             index 0
             ouput 0]
        (if (< index frac-bits)
          (let [power-slice (frac-digit-power index)]
            (if (not (frac-digits-lt digits-left power-slice))
              (recur (frac-digits-sub digits-left power-slice)
                     (inc index)
                     (bit-set ouput (- (dec frac-bits) index)))
              (recur digits-left
                     (inc index)
                     ouput)))
          ouput))
      (throw (str "Bad format for Frac number: " input))))
  )

(defn show-ast [ast]
  (|case ast
    [_ ($BoolS ?value)]
    (pr-str ?value)

    [_ ($NatS ?value)]
    (str "+" (Long/toUnsignedString ?value))

    [_ ($IntS ?value)]
    (pr-str ?value)

    [_ ($FracS ?value)]
    (encode-frac ?value)

    [_ ($RealS ?value)]
    (pr-str ?value)

    [_ ($CharS ?value)]
    (str "#\"" (pr-str ?value) "\"")

    [_ ($TextS ?value)]
    (str "\"" ?value "\"")

    [_ ($TagS ?module ?tag)]
    (if (.equals "" ?module)
      (str "#" ?tag)
      (str "#" ?module ";" ?tag))

    [_ ($SymbolS ?module ?name)]
    (if (.equals "" ?module)
      ?name
      (str ?module ";" ?name))

    [_ ($TupleS ?elems)]
    (str "[" (->> ?elems (|map show-ast) (|interpose " ") (fold str "")) "]")

    [_ ($RecordS ?elems)]
    (str "{" (->> ?elems
                  (|map (fn [elem]
                          (|let [[k v] elem]
                            (str (show-ast k) " " (show-ast v)))))
                  (|interpose " ") (fold str "")) "}")

    [_ ($FormS ?elems)]
    (str "(" (->> ?elems (|map show-ast) (|interpose " ") (fold str "")) ")")

    _
    (assert false (prn-str 'show-ast (adt->text ast)))
    ))

(defn ident->text [ident]
  (|let [[?module ?name] ident]
    (if (= "" ?module)
      ?name
      (str ?module ";" ?name))))

(defn fold2% [f init xs ys]
  (|case [xs ys]
    [($Cons x xs*) ($Cons y ys*)]
    (|do [init* (f init x y)]
      (fold2% f init* xs* ys*))

    [($Nil) ($Nil)]
    (return init)

    [_ _]
    (assert false "Lists don't match in size.")))

(defn map2% [f xs ys]
  (|case [xs ys]
    [($Cons x xs*) ($Cons y ys*)]
    (|do [z (f x y)
          zs (map2% f xs* ys*)]
      (return ($Cons z zs)))

    [($Nil) ($Nil)]
    (return $Nil)

    [_ _]
    (assert false "Lists don't match in size.")))

(defn map2 [f xs ys]
  (|case [xs ys]
    [($Cons x xs*) ($Cons y ys*)]
    ($Cons (f x y) (map2 f xs* ys*))

    [_ _]
    $Nil))

(defn fold2 [f init xs ys]
  (|case [xs ys]
    [($Cons x xs*) ($Cons y ys*)]
    (and init
         (fold2 f (f init x y) xs* ys*))

    [($Nil) ($Nil)]
    init

    [_ _]
    init
    ;; (assert false)
    ))

(defn ^:private enumerate* [idx xs]
  "(All [a] (-> Int (List a) (List (, Int a))))"
  (|case xs
    ($Cons x xs*)
    ($Cons (T [idx x])
           (enumerate* (inc idx) xs*))

    ($Nil)
    xs
    ))

(defn enumerate [xs]
  "(All [a] (-> (List a) (List (, Int a))))"
  (enumerate* 0 xs))

(def modules
  "(Lux (List Text))"
  (fn [state]
    (return* state (|keys (get$ $modules state)))))

(defn when% [test body]
  "(-> Bool (Lux Unit) (Lux Unit))"
  (if test
    body
    (return unit-tag)))

(defn |at [idx xs]
  "(All [a] (-> Int (List a) (Maybe a)))"
  (|case xs
    ($Cons x xs*)
    (cond (< idx 0)
          $None

          (= idx 0)
          ($Some x)

          :else ;; > 1
          (|at (dec idx) xs*))

    ($Nil)
    $None
    ))

(defn normalize [ident]
  "(-> Ident (Lux Ident))"
  (|case ident
    ["" name] (|do [module get-module-name]
                (return (T [module name])))
    _ (return ident)))

(defn ident= [x y]
  (|let [[xmodule xname] x
         [ymodule yname] y]
    (and (= xmodule ymodule)
         (= xname yname))))

(defn |list-put [idx val xs]
  (|case xs
    ($Nil)
    $None
    
    ($Cons x xs*)
    (if (= idx 0)
      ($Some ($Cons val xs*))
      (|case (|list-put (dec idx) val xs*)
        ($None)      $None
        ($Some xs**) ($Some ($Cons x xs**)))
      )))

(do-template [<flagger> <asker> <tag>]
  (do (defn <flagger> [module]
        "(-> Text (Lux Unit))"
        (fn [state]
          (let [state* (update$ $host (fn [host]
                                        (update$ $module-states
                                                 (fn [module-states]
                                                   (|put module <tag> module-states))
                                                 host))
                                state)]
            ($Right (T [state* unit-tag])))))
    (defn <asker> [module]
      "(-> Text (Lux Bool))"
      (fn [state]
        (if-let [module-state (->> state (get$ $host) (get$ $module-states) (|get module))]
          ($Right (T [state (|case module-state
                              (<tag>) true
                              _       false)]))
          ($Right (T [state false])))
        )))

  flag-active-module   active-module?   $Active
  flag-compiled-module compiled-module? $Compiled
  flag-cached-module   cached-module?   $Cached
  )

(do-template [<name> <default> <op>]
  (defn <name> [p xs]
    "(All [a] (-> (-> a Bool) (List a) Bool))"
    (|case xs
      ($Nil)
      <default>

      ($Cons x xs*)
      (<op> (p x) (<name> p xs*))))

  |every? true  and
  |any?   false or)

(defn m-comp [f g]
  "(All [a b c] (-> (-> b (Lux c)) (-> a (Lux b)) (-> a (Lux c))))"
  (fn [x]
    (|do [y (g x)]
      (f y))))

(defn with-attempt [m-value on-error]
  "(All [a] (-> (Lux a) (-> Text (Lux a)) (Lux a)))"
  (fn [state]
    (|case (m-value state)
      ($Left msg)
      ((on-error msg) state)
      
      output
      output)))

(defn |some [f xs]
  "(All [a b] (-> (-> a (Maybe b)) (List a) (Maybe b)))"
  (|case xs
    ($Nil)
    $None

    ($Cons x xs*)
    (|case (f x)
      ($None) (|some f xs*)
      output  output)
    ))

(def get-type-env
  "(Lux TypeEnv)"
  (fn [state]
    (return* state (->> state (get$ $host) (get$ $type-env)))))

(defn with-type-env [type-env body]
  "(All [a] (-> TypeEnv (Lux a) (Lux a)))"
  (fn [state]
    (|let [state* (update$ $host #(update$ $type-env (partial |++ type-env) %)
                           state)]
      (|case (body state*)
        ($Right [state** output])
        ($Right (T [(update$ $host
                             #(set$ $type-env
                                    (->> state (get$ $host) (get$ $type-env))
                                    %)
                             state**)
                    output]))

        ($Left msg)
        ($Left msg)))))

(defn |take [n xs]
  (|case (T [n xs])
    [0 _]             $Nil
    [_ ($Nil)]        $Nil
    [_ ($Cons x xs*)] ($Cons x (|take (dec n) xs*))
    ))

(defn |drop [n xs]
  (|case (T [n xs])
    [0 _]             xs
    [_ ($Nil)]        $Nil
    [_ ($Cons x xs*)] (|drop (dec n) xs*)
    ))

(defn |but-last [xs]
  (|case xs
    ($Nil)
    $Nil
    
    ($Cons x ($Nil))
    $Nil

    ($Cons x xs*)
    ($Cons x (|but-last xs*))

    _
    (assert false (adt->text xs))))

(defn |partition [n xs]
  (->> xs ->seq (partition-all n) (map ->list) ->list))

(defn with-scope-type-var [id body]
  (fn [state]
    (|case (body (set$ $scope-type-vars
                       ($Cons id (get$ $scope-type-vars state))
                       state))
      ($Right [state* output])
      ($Right (T [(set$ $scope-type-vars
                        (get$ $scope-type-vars state)
                        state*)
                  output]))

      ($Left msg)
      ($Left msg))))

(defn push-dummy-name [real-name store-name]
  (fn [state]
    ($Right (T [(update$ $host
                         #(update$ $dummy-mappings
                                   (partial $Cons (T [real-name store-name]))
                                   %)
                         state)
                nil]))))

(def pop-dummy-name
  (fn [state]
    ($Right (T [(update$ $host
                         #(update$ $dummy-mappings
                                   |tail
                                   %)
                         state)
                nil]))))

(defn de-alias-class [class-name]
  (fn [state]
    ($Right (T [state
                (|case (|some #(|let [[real-name store-name] %]
                                 (if (= real-name class-name)
                                   ($Some store-name)
                                   $None))
                              (->> state (get$ $host) (get$ $dummy-mappings)))
                  ($Some store-name)
                  store-name

                  _
                  class-name)]))))

(defn |eitherL [left right]
  (fn [compiler]
    (|case (run-state left compiler)
      ($Left _error)
      (run-state right compiler)

      _output
      _output)))
