(ns rejax.runner
    (:require
      [doo.runner :refer-macros [doo-tests]]
      [rejax.demo-test]))


(doo-tests 'rejax.demo-test)
