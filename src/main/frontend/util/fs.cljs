;; TODO: move all file path related util functions to here (excepts those fit graph-parser)

(ns frontend.util.fs
  "Misc util fns built on top of frontend.fs"
  (:require ["path" :as node-path]
            [logseq.graph-parser.util :as gp-util]
            [clojure.string :as string]
            [frontend.state :as state]
            [frontend.fs :as fs]
            [frontend.config :as config]
            [promesa.core :as p]
            [cljs.reader :as reader]
            [frontend.worker.file.util :as wfu]))

;; NOTE: This is not the same ignored-path? as src/electron/electron/utils.cljs.
;;       The assets directory is ignored.
;;
;; When in nfs-mode, dir is "", path is relative path to graph dir.
;; When in native-mode, dir and path are absolute paths.
(defn ignored-path?
  "Ignore path for ls-dir-files-with-handler! and reload-dir!"
  [dir path]
  (let [ignores ["." ".recycle" "node_modules" "logseq/bak"
                 "logseq/version-files" "logseq/graphs-txid.edn"]]
    (when (string? path)
      (or
       (some #(string/starts-with? path
                                   (if (= dir "")
                                     %
                                     (str dir "/" %))) ignores)
       (some #(string/includes? path (if (= dir "")
                                       (str "/" % "/")
                                       (str % "/"))) ignores)
       (some #(string/ends-with? path %)
             [".DS_Store" "logseq/graphs-txid.edn"])
      ;; hidden directory or file
       (let [relpath (node-path/relative dir path)]
         (or (re-find #"/\.[^.]+" relpath)
             (re-find #"^\.[^.]+" relpath)))
       (let [path (string/lower-case path)]
         (and
          (not (string/blank? (node-path/extname path)))
          (not
           (some #(string/ends-with? path %)
                 [".md" ".markdown" ".org" ".js" ".edn" ".css"]))))))))

(defn read-graphs-txid-info
  [root]
  (when (string? root)
    (p/let [exists? (fs/file-exists? root "logseq/graphs-txid.edn")]
      (when exists?
        (-> (p/let [txid-str (fs/read-file root "logseq/graphs-txid.edn")
                    txid-meta (and txid-str (reader/read-string txid-str))]
              txid-meta)
            (p/catch
                (fn [^js e]
                  (js/console.error "[fs read txid data error]" e))))))))

(defn inflate-graphs-info
  [graphs]
  (if (seq graphs)
    (p/all (for [{:keys [root] :as graph} graphs]
             (p/let [sync-meta (read-graphs-txid-info root)]
               (if sync-meta
                 (assoc graph
                        :sync-meta sync-meta
                        :GraphUUID (second sync-meta))
                 graph))))
    []))

(defn read-repo-file
  [repo-url file-rpath]
  (when-let [repo-dir (config/get-repo-dir repo-url)]
    (fs/read-file repo-dir file-rpath)))

(def include-reserved-chars? wfu/include-reserved-chars?)
(def windows-reserved-filebodies wfu/windows-reserved-filebodies)
(def file-name-sanity wfu/file-name-sanity)

(defn create-title-property?
  [page-name]
  (and (string? page-name)
       (let [filename-format (state/get-filename-format)
             file-name  (file-name-sanity page-name filename-format)
             page-name' (gp-util/title-parsing file-name filename-format)
             result     (or (not= page-name page-name')
                            (include-reserved-chars? file-name))]
         result)))
