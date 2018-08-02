(ns cljs-react-navigation.re-frame
  (:require [cljs-react-navigation.base :as base]
            [cljs-react-navigation.reagent :as reagent]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch dispatch-sync reg-event-db trim-v reg-sub]]))

(defn log-console
  [x]
  (.log js/console x))

(defn ->js
  "Wrapper around clj->js that preserves namespaced keywords"
  [x]
  (clj->js x :keyword-fn #(.-fqn %)))

(defn ->clj
  [x]
  (js->clj x :keywordize-keys true))


(def ref-getStateForAction (atom nil))                      ;; HACK

(reg-event-db
  ::swap-routing-state
  [trim-v]
  (fn [app-db [new-routes]]
    (assoc app-db :routing new-routes)))

(reg-event-db
  ::dispatch
  [trim-v]
  (fn [app-db [dispatch-args]]
    (let [routing-state (get app-db :routing)
          type (aget dispatch-args "type")
          action-fn (get reagent/NavigationActionsMap type)
          action (action-fn dispatch-args)
          new-state (@ref-getStateForAction action (->js routing-state))]
      (assoc app-db :routing (->clj new-state)))))

(reg-event-db
  ::navigate
  [trim-v]
  (fn [app-db [routeName params]]
    ;(log-console (str "Navigating to " routeName " with params " params))
    (let [routing-state (get app-db :routing)
          action-fn (get reagent/NavigationActionsMap "Navigation/NAVIGATE")
          action (action-fn (->js {:routeName routeName :params params}))
          new-state (@ref-getStateForAction action (->js routing-state))]
      (assoc app-db :routing (->clj new-state)))))


(reg-event-db
  ::goBack
  [trim-v]
  (fn [app-db [routeName]]
    ;(log-console (str "Navigating back to: " routeName))
    (let [routing-state (get app-db :routing)
          action-fn (get reagent/NavigationActionsMap "Navigation/BACK")
          action (action-fn (->js {:routeName routeName}))
          new-state (@ref-getStateForAction action (->js routing-state))]
      (log-console (str "  with new state: " new-state))
      (assoc app-db :routing (->clj new-state)))))

(reg-sub
  ::routing-state
  (fn [app-db]
    (get-in app-db [:routing])))


;; API
(def stack-screen reagent/stack-screen)
(def tab-screen reagent/tab-screen)
(def drawer-component reagent/drawer-component)
(def stack-navigator reagent/stack-navigator)
(def tab-navigator reagent/tab-navigator)
(def drawer-navigator reagent/drawer-navigator)
(def switch-navigator reagent/switch-navigator)

(def init-state
  (fn [main key]
    (-> main
        .-router
        (as-> router
              (.getStateForAction router
                                  (.getActionForPathAndParams router (name key)))))))

(def nil-fn (fn [_]))

(defn router [{:keys [root-router init-route-name add-listener]
               :or   {add-listener nil-fn init-route-name :start-route}
               :as   props}]
  (let [routing-sub (subscribe [::routing-state])
        getStateForAction (aget root-router "router" "getStateForAction")]
    (reset! ref-getStateForAction getStateForAction)
    (fn [props]
      (let [routing-state (or @routing-sub
                              (init-state root-router init-route-name))]
        ;(log-console (str "routing-state: " routing-state))
        [:> root-router {:navigation
                         (addNavigationHelpers
                          (clj->js {:state    routing-state
                                    :addListener add-listener
                                    :dispatch (fn [action]
                                                (let [next-state (getStateForAction action routing-state)]
                                                  (dispatch [::swap-routing-state next-state])))}))}]))))
