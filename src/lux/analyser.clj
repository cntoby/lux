;;   Copyright (c) Eduardo Julian. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns lux.analyser
  (:require (clojure [template :refer [do-template]])
            clojure.core.match
            clojure.core.match.array
            (lux [base :as & :refer [|let |do return fail return* fail* |case]]
                 [reader :as &reader]
                 [parser :as &parser]
                 [type :as &type]
                 [host :as &host])
            (lux.analyser [base :as &&]
                          [lux :as &&lux]
                          [host :as &&host]
                          [module :as &&module])))

;; [Utils]
(defn ^:private parse-handler [[catch+ finally+] token]
  (|case token
    (&/$Meta meta (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_catch"))
                                     (&/$Cons (&/$Meta _ (&/$TextS ?ex-class))
                                              (&/$Cons (&/$Meta _ (&/$SymbolS "" ?ex-arg))
                                                       (&/$Cons ?catch-body
                                                                (&/$Nil)))))))
    (return (&/T (&/|++ catch+ (&/|list (&/T ?ex-class ?ex-arg ?catch-body))) finally+))

    (&/$Meta meta (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_finally"))
                                     (&/$Cons ?finally-body
                                              (&/$Nil)))))
    (return (&/T catch+ (&/V &/$Some ?finally-body)))

    _
    (fail (str "[Analyser Error] Wrong syntax for exception handler: " (&/show-ast token)))))

(defn ^:private parse-tag [ast]
  (|case ast
    (&/$Meta _ (&/$TagS "" name))
    (return name)
    
    _
    (fail (str "[Analyser Error] Not a tag: " (&/show-ast ast)))))

(defn ^:private aba7 [analyse eval! compile-module compile-token exo-type token]
  (|case token
    ;; Arrays
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_new-array"))
                       (&/$Cons (&/$Meta _ (&/$SymbolS _ ?class))
                                (&/$Cons (&/$Meta _ (&/$IntS ?length))
                                         (&/$Nil)))))
    (&&host/analyse-jvm-new-array analyse ?class ?length)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_aastore"))
                       (&/$Cons ?array
                                (&/$Cons (&/$Meta _ (&/$IntS ?idx))
                                         (&/$Cons ?elem
                                                  (&/$Nil))))))
    (&&host/analyse-jvm-aastore analyse ?array ?idx ?elem)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_aaload"))
                       (&/$Cons ?array
                                (&/$Cons (&/$Meta _ (&/$IntS ?idx))
                                         (&/$Nil)))))
    (&&host/analyse-jvm-aaload analyse ?array ?idx)

    ;; Classes & interfaces
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_class"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?name))
                                (&/$Cons (&/$Meta _ (&/$TextS ?super-class))
                                         (&/$Cons (&/$Meta _ (&/$TupleS ?interfaces))
                                                  (&/$Cons (&/$Meta _ (&/$TupleS ?fields))
                                                           (&/$Cons (&/$Meta _ (&/$TupleS ?methods))
                                                                    (&/$Nil))))))))
    (&&host/analyse-jvm-class analyse compile-token ?name ?super-class ?interfaces ?fields ?methods)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_interface"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?name))
                                (&/$Cons (&/$Meta _ (&/$TupleS ?supers))
                                         ?methods))))
    (&&host/analyse-jvm-interface analyse compile-token ?name ?supers ?methods)

    ;; Programs
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_program"))
                       (&/$Cons (&/$Meta _ (&/$SymbolS "" ?args))
                                (&/$Cons ?body
                                         (&/$Nil)))))
    (&&host/analyse-jvm-program analyse compile-token ?args ?body)
    
    _
    (fail "")))

(defn ^:private aba6 [analyse eval! compile-module compile-token exo-type token]
  (|case token
    ;; Primitive conversions
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_d2f")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-d2f analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_d2i")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-d2i analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_d2l")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-d2l analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_f2d")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-f2d analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_f2i")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-f2i analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_f2l")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-f2l analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_i2b")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-i2b analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_i2c")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-i2c analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_i2d")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-i2d analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_i2f")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-i2f analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_i2l")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-i2l analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_i2s")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-i2s analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_l2d")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-l2d analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_l2f")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-l2f analyse exo-type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_l2i")) (&/$Cons ?value (&/$Nil))))
    (&&host/analyse-jvm-l2i analyse exo-type ?value)

    ;; Bitwise operators
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_iand")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-iand analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_ior")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-ior analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_ixor")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-ixor analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_ishl")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-ishl analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_ishr")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-ishr analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_iushr")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-iushr analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_land")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-land analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_lor")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-lor analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_lxor")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-lxor analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_lshl")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-lshl analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_lshr")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-lshr analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_lushr")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-lushr analyse exo-type ?x ?y)

    _
    (aba7 analyse eval! compile-module compile-token exo-type token)))

(defn ^:private aba5 [analyse eval! compile-module compile-token exo-type token]
  (|case token
    ;; Objects
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_null?"))
                       (&/$Cons ?object
                                (&/$Nil))))
    (&&host/analyse-jvm-null? analyse exo-type ?object)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_instanceof"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?class))
                                (&/$Cons ?object
                                         (&/$Nil)))))
    (&&host/analyse-jvm-instanceof analyse exo-type ?class ?object)
    
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_new"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?class))
                                (&/$Cons (&/$Meta _ (&/$TupleS ?classes))
                                         (&/$Cons (&/$Meta _ (&/$TupleS ?args))
                                                  (&/$Nil))))))
    (&&host/analyse-jvm-new analyse exo-type ?class ?classes ?args)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_getstatic"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?class))
                                (&/$Cons (&/$Meta _ (&/$TextS ?field))
                                         (&/$Nil)))))
    (&&host/analyse-jvm-getstatic analyse exo-type ?class ?field)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_getfield"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?class))
                                (&/$Cons (&/$Meta _ (&/$TextS ?field))
                                         (&/$Cons ?object
                                                  (&/$Nil))))))
    (&&host/analyse-jvm-getfield analyse exo-type ?class ?field ?object)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_putstatic"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?class))
                                (&/$Cons (&/$Meta _ (&/$TextS ?field))
                                         (&/$Cons ?value
                                                  (&/$Nil))))))
    (&&host/analyse-jvm-putstatic analyse exo-type ?class ?field ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_putfield"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?class))
                                (&/$Cons (&/$Meta _ (&/$TextS ?field))
                                         (&/$Cons ?object
                                                  (&/$Cons ?value
                                                           (&/$Nil)))))))
    (&&host/analyse-jvm-putfield analyse exo-type ?class ?field ?object ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_invokestatic"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?class))
                                (&/$Cons (&/$Meta _ (&/$TextS ?method))
                                         (&/$Cons (&/$Meta _ (&/$TupleS ?classes))
                                                  (&/$Cons (&/$Meta _ (&/$TupleS ?args))
                                                           (&/$Nil)))))))
    (&&host/analyse-jvm-invokestatic analyse exo-type ?class ?method ?classes ?args)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_invokevirtual"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?class))
                                (&/$Cons (&/$Meta _ (&/$TextS ?method))
                                         (&/$Cons (&/$Meta _ (&/$TupleS ?classes))
                                                  (&/$Cons ?object
                                                           (&/$Cons (&/$Meta _ (&/$TupleS ?args))
                                                                    (&/$Nil))))))))
    (&&host/analyse-jvm-invokevirtual analyse exo-type ?class ?method ?classes ?object ?args)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_invokeinterface"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?class))
                                (&/$Cons (&/$Meta _ (&/$TextS ?method))
                                         (&/$Cons (&/$Meta _ (&/$TupleS ?classes))
                                                  (&/$Cons ?object
                                                           (&/$Cons (&/$Meta _ (&/$TupleS ?args))
                                                                    (&/$Nil))))))))
    (&&host/analyse-jvm-invokeinterface analyse exo-type ?class ?method ?classes ?object ?args)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_invokespecial"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?class))
                                (&/$Cons (&/$Meta _ (&/$TextS ?method))
                                         (&/$Cons (&/$Meta _ (&/$TupleS ?classes))
                                                  (&/$Cons ?object
                                                           (&/$Cons (&/$Meta _ (&/$TupleS ?args))
                                                                    (&/$Nil))))))))
    (&&host/analyse-jvm-invokespecial analyse exo-type ?class ?method ?classes ?object ?args)

    ;; Exceptions
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_try"))
                       (&/$Cons ?body
                                ?handlers)))
    (|do [catches+finally (&/fold% parse-handler (&/T (&/|list) (&/V &/$None nil)) ?handlers)]
      (&&host/analyse-jvm-try analyse exo-type ?body catches+finally))

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_throw"))
                       (&/$Cons ?ex
                                (&/$Nil))))
    (&&host/analyse-jvm-throw analyse exo-type ?ex)

    ;; Syncronization/monitos
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_monitorenter"))
                       (&/$Cons ?monitor
                                (&/$Nil))))
    (&&host/analyse-jvm-monitorenter analyse exo-type ?monitor)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_monitorexit"))
                       (&/$Cons ?monitor
                                (&/$Nil))))
    (&&host/analyse-jvm-monitorexit analyse exo-type ?monitor)

    _
    (aba6 analyse eval! compile-module compile-token exo-type token)))

(defn ^:private aba4 [analyse eval! compile-module compile-token exo-type token]
  (|case token
    ;; Float arithmetic
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_fadd")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-fadd analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_fsub")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-fsub analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_fmul")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-fmul analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_fdiv")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-fdiv analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_frem")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-frem analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_feq")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-feq analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_flt")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-flt analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_fgt")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-fgt analyse exo-type ?x ?y)

    ;; Double arithmetic
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_dadd")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-dadd analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_dsub")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-dsub analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_dmul")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-dmul analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_ddiv")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-ddiv analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_drem")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-drem analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_deq")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-deq analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_dlt")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-dlt analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_dgt")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-dgt analyse exo-type ?x ?y)
    
    _
    (aba5 analyse eval! compile-module compile-token exo-type token)))

(defn ^:private aba3 [analyse eval! compile-module compile-token exo-type token]
  (|case token
    ;; Host special forms
    ;; Characters
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_ceq")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-ceq analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_clt")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-clt analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_cgt")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-cgt analyse exo-type ?x ?y)
    
    ;; Integer arithmetic
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_iadd")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-iadd analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_isub")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-isub analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_imul")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-imul analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_idiv")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-idiv analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_irem")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-irem analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_ieq")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-ieq analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_ilt")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-ilt analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_igt")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-igt analyse exo-type ?x ?y)

    ;; Long arithmetic
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_ladd")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-ladd analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_lsub")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-lsub analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_lmul")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-lmul analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_ldiv")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-ldiv analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_lrem")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-lrem analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_leq")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-leq analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_llt")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-llt analyse exo-type ?x ?y)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_jvm_lgt")) (&/$Cons ?x (&/$Cons ?y (&/$Nil)))))
    (&&host/analyse-jvm-lgt analyse exo-type ?x ?y)

    _
    (aba4 analyse eval! compile-module compile-token exo-type token)))

(defn ^:private aba2 [analyse eval! compile-module compile-token exo-type token]
  (|case token
    (&/$SymbolS ?ident)
    (&&lux/analyse-symbol analyse exo-type ?ident)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_lux_case"))
                       (&/$Cons ?value ?branches)))
    (&&lux/analyse-case analyse exo-type ?value ?branches)
    
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_lux_lambda"))
                       (&/$Cons (&/$Meta _ (&/$SymbolS "" ?self))
                                (&/$Cons (&/$Meta _ (&/$SymbolS "" ?arg))
                                         (&/$Cons ?body
                                                  (&/$Nil))))))
    (&&lux/analyse-lambda analyse exo-type ?self ?arg ?body)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_lux_def"))
                       (&/$Cons (&/$Meta _ (&/$SymbolS "" ?name))
                                (&/$Cons ?value
                                         (&/$Nil)))))
    (&&lux/analyse-def analyse compile-token ?name ?value)
    
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_lux_declare-macro"))
                       (&/$Cons (&/$Meta _ (&/$SymbolS "" ?name))
                                (&/$Nil))))
    (&&lux/analyse-declare-macro analyse compile-token ?name)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_lux_declare-tags"))
                       (&/$Cons (&/$Meta _ (&/$TupleS tags))
                                (&/$Nil))))
    (|do [tags* (&/map% parse-tag tags)]
      (&&lux/analyse-declare-tags tags*))
    
    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_lux_import"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?path))
                                (&/$Nil))))
    (&&lux/analyse-import analyse compile-module compile-token ?path)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_lux_:"))
                       (&/$Cons ?type
                                (&/$Cons ?value
                                         (&/$Nil)))))
    (&&lux/analyse-check analyse eval! exo-type ?type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_lux_:!"))
                       (&/$Cons ?type
                                (&/$Cons ?value
                                         (&/$Nil)))))
    (&&lux/analyse-coerce analyse eval! exo-type ?type ?value)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_lux_export"))
                       (&/$Cons (&/$Meta _ (&/$SymbolS "" ?ident))
                                (&/$Nil))))
    (&&lux/analyse-export analyse compile-token ?ident)

    (&/$FormS (&/$Cons (&/$Meta _ (&/$SymbolS _ "_lux_alias"))
                       (&/$Cons (&/$Meta _ (&/$TextS ?alias))
                                (&/$Cons (&/$Meta _ (&/$TextS ?module))
                                         (&/$Nil)))))
    (&&lux/analyse-alias analyse compile-token ?alias ?module)
    
    _
    (aba3 analyse eval! compile-module compile-token exo-type token)))

(defn ^:private aba1 [analyse eval! compile-module compile-token exo-type token]
  (|case token
    ;; Standard special forms
    (&/$BoolS ?value)
    (|do [_ (&type/check exo-type &type/Bool)]
      (return (&/|list (&/T (&/V &&/$bool ?value) exo-type))))

    (&/$IntS ?value)
    (|do [_ (&type/check exo-type &type/Int)]
      (return (&/|list (&/T (&/V &&/$int ?value) exo-type))))

    (&/$RealS ?value)
    (|do [_ (&type/check exo-type &type/Real)]
      (return (&/|list (&/T (&/V &&/$real ?value) exo-type))))

    (&/$CharS ?value)
    (|do [_ (&type/check exo-type &type/Char)]
      (return (&/|list (&/T (&/V &&/$char ?value) exo-type))))

    (&/$TextS ?value)
    (|do [_ (&type/check exo-type &type/Text)]
      (return (&/|list (&/T (&/V &&/$text ?value) exo-type))))

    (&/$TupleS ?elems)
    (&&lux/analyse-tuple analyse exo-type ?elems)

    (&/$RecordS ?elems)
    (&&lux/analyse-record analyse exo-type ?elems)

    (&/$TagS ?ident)
    (|do [[module tag-name] (&/normalize ?ident)
          idx (&&module/tag-index module tag-name)]
      (&&lux/analyse-variant analyse exo-type idx (&/|list)))
    
    (&/$SymbolS _ "_jvm_null")
    (&&host/analyse-jvm-null analyse exo-type)

    _
    (aba2 analyse eval! compile-module compile-token exo-type token)
    ))

(defn ^:private add-loc [meta ^String msg]
  (if (.startsWith msg "@")
    msg
    (|let [[file line col] meta]
      (str "@ " file "," line "," col "\n" msg))))

(defn ^:private analyse-basic-ast [analyse eval! compile-module compile-token exo-type token]
  ;; (prn 'analyse-basic-ast (&/show-ast token))
  (|case token
    (&/$Meta meta ?token)
    (fn [state]
      (|case (try ((aba1 analyse eval! compile-module compile-token exo-type ?token) state)
               (catch Error e
                 (prn e)
                 (assert false (prn-str 'analyse-basic-ast (&/show-ast token)))))
        (&/$Right state* output)
        (return* state* output)

        (&/$Left "")
        (fail* (add-loc (&/get$ &/$cursor state) (str "[Analyser Error] Unrecognized token: " (&/show-ast token))))

        (&/$Left msg)
        (fail* (add-loc (&/get$ &/$cursor state) msg))
        ))
    ))

(defn ^:private just-analyse [analyser syntax]
  (&type/with-var
    (fn [?var]
      (|do [[?output-term ?output-type] (&&/analyse-1 analyser ?var syntax)]
        (|case [?var ?output-type]
          [(&/$VarT ?e-id) (&/$VarT ?a-id)]
          (if (= ?e-id ?a-id)
            (|do [?output-type* (&type/deref ?e-id)]
              (return (&/T ?output-term ?output-type*)))
            (return (&/T ?output-term ?output-type)))

          [_ _]
          (return (&/T ?output-term ?output-type)))
        ))))

(defn ^:private analyse-ast [eval! compile-module compile-token exo-type token]
  ;; (prn 'analyse-ast (&/show-ast token))
  (&/with-cursor (aget token 1 0)
    (&/with-expected-type exo-type
      (|case token
        (&/$Meta meta (&/$FormS (&/$Cons (&/$Meta _ (&/$IntS idx)) ?values)))
        (&&lux/analyse-variant (partial analyse-ast eval! compile-module compile-token) exo-type idx ?values)

        (&/$Meta meta (&/$FormS (&/$Cons (&/$Meta _ (&/$TagS ?ident)) ?values)))
        (|do [;; :let [_ (println 'analyse-ast/_0 (&/ident->text ?ident))]
              [module tag-name] (&/normalize ?ident)
              ;; :let [_ (println 'analyse-ast/_1 (&/ident->text (&/T module tag-name)))]
              idx (&&module/tag-index module tag-name)
              ;; :let [_ (println 'analyse-ast/_2 idx)]
              ]
          (&&lux/analyse-variant (partial analyse-ast eval! compile-module compile-token) exo-type idx ?values))
        
        (&/$Meta meta (&/$FormS (&/$Cons ?fn ?args)))
        (fn [state]
          (|case ((just-analyse (partial analyse-ast eval! compile-module compile-token) ?fn) state)
            (&/$Right state* =fn)
            (do ;; (prn 'GOT_FUN (&/show-ast ?fn) (&/show-ast token) (aget =fn 0 0) (aget =fn 1 0))
                ((&&lux/analyse-apply (partial analyse-ast eval! compile-module compile-token) exo-type meta =fn ?args) state*))

            _
            ((analyse-basic-ast (partial analyse-ast eval! compile-module compile-token) eval! compile-module compile-token exo-type token) state)))
        
        _
        (analyse-basic-ast (partial analyse-ast eval! compile-module compile-token) eval! compile-module compile-token exo-type token)))))

;; [Resources]
(defn analyse [eval! compile-module compile-token]
  (|do [asts &parser/parse]
    (&/flat-map% (partial analyse-ast eval! compile-module compile-token &type/$Void) asts)))
