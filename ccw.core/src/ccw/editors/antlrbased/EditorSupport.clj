;*******************************************************************************
;* Copyright (c) 2010 Laurent PETIT.
;* All rights reserved. This program and the accompanying materials
;* are made available under the terms of the Eclipse Public License v1.0
;* which accompanies this distribution, and is available at
;* http://www.eclipse.org/legal/epl-v10.html
;*
;* Contributors: 
;*    Laurent PETIT - initial API and implementation
;*******************************************************************************/

(ns ccw.editors.antlrbased.EditorSupport 
  (:require [paredit.parser :as p])
  (:import [org.eclipse.jdt.ui PreferenceConstants])
  (:gen-class
    :methods [^{:static true} [updateParseRef [String Object] Object]
              ^{:static true} [getParser [String Object] Object]
              ^{:static true} [startWatchParseRef [Object Object] Object]
              ^{:static true} [disposeSourceViewerDecorationSupport [Object] org.eclipse.ui.texteditor.SourceViewerDecorationSupport]
              ^{:static true} [configureSourceViewerDecorationSupport [Object Object] Object]]))

; TODO move in a utility namespace, or remove
(defprotocol Cancellable (isCancelled [this]) (cancel [this]))

; TODO move in a utility namespace, or remove
(defn timed-delay [pause fun]
  (let [d (delay (fun))
        f (future (Thread/sleep pause) @d)]
    (reify
      clojure.lang.IDeref 
        (deref [_] @d) 
      Cancellable
        (isCancelled [_] (future-cancelled? f))
        (cancel [_] (future-cancel f)))))

(defn -updateParseRef [text r]
  (let [r (if (nil? r) (ref nil) r)] 
    (dosync
      (if-let [rv @r] (cancel (:parser rv)))
      (ref-set r {:text text :parser (timed-delay 800 #(try (p/parse text) (catch Exception _ nil)))}))
    r))

(defn -startWatchParseRef [r editor]
  (add-watch r :track-state (fn [_ _ _ new-state] 
                              (.setStructuralEditionPossible editor 
                                (let [possible? (not (nil? @(:parser new-state)))
                                      possible? (or possible? (.isEmpty (:text new-state)))]
                                  possible?)))))

(defn -getParser [text r]
  (let [rv @r] 
    (if (= text (:text rv))
      @(:parser rv)
      (do
        (println "cached parser miss !")
        (-updateParseRef text r)
        (recur text r)))))

(defn -disposeSourceViewerDecorationSupport [s]
  (when s
    (doto s .uninstall .dispose)
    nil))

(defn -configureSourceViewerDecorationSupport [support viewer]
		;; TODO more to pick in configureSourceViewerDecorationSupport of AbstractDecoratedTextEditor, if you want ...
  (doto support
		(.setCharacterPairMatcher (.getPairsMatcher viewer))
		(.setMatchingCharacterPainterPreferenceKeys 
      PreferenceConstants/EDITOR_MATCHING_BRACKETS 
      PreferenceConstants/EDITOR_MATCHING_BRACKETS_COLOR)))