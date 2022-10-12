^{:nextjournal.clerk/visibility {:code :hide}}
(ns nextjournal.clerk.homepage
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [cards-macro :as c])
  (:import (javax.imageio ImageIO)
           (java.net URL)))

(def clerk-type
  [:svg {:width "135", :height "62", :viewbox "0 0 135 62", :fill "none", :xmlns "http://www.w3.org/2000/svg" :alt "Clerk"}
   [:path {:fill-rule "evenodd", :clip-rule "evenodd", :d "M19.9531 39.1211V52.569C19.9531 57.4591 17.4589 59.9042 12.4707 59.9042H9.88746C4.81011 59.9042 2.31598 57.4591 2.31598 52.569V9.43099C2.31598 4.54084 4.81011 2.09577 9.88746 2.09577H12.3816C17.3699 2.09577 19.864 4.54084 19.864 9.43099V21.0451H22.18V9.43099C22.18 3.23099 18.8842 0 12.3816 0H9.88746C3.38489 0 0 3.23099 0 9.43099V52.4817C0 58.8563 3.29582 62 9.88746 62H12.4707C18.9732 62 22.2691 58.8563 22.2691 52.4817V39.1211H19.9531ZM31.6784 0.436619V61.5634H48.3357V59.4676H33.9944V0.436619H31.6784ZM55.4263 61.5634V0.436619H72.7962V2.53239H57.7423V29.4282H72.3508V31.5239H57.7423V59.4676H72.9744V61.5634H55.4263ZM102.974 22.093V9.95493C102.974 3.58028 99.6778 0.436619 93.1752 0.436619H82.2188V61.5634H84.5348V32.9211C84.6073 32.9206 84.6794 32.9202 84.7513 32.9197L92.5106 46.0464L100.538 61.179H103.197L87.204 32.9086C88.7195 32.9043 90.2316 32.9043 92.0172 32.9043C97.7395 32.9043 102.974 30.0625 102.974 22.093ZM100.658 22.3549C100.658 28.031 97.8072 30.8253 92.0172 30.8253H84.5348V2.53239H93.0861C98.1635 2.53239 100.658 4.97747 100.658 9.86761V22.3549ZM118.165 30.5634L134.644 61.5634H132.15L124.044 46.2817L115.938 31V61.5634H113.622V31V0.436619H115.938V30.4761L132.595 0.436619H135L118.165 30.5634Z", :fill "currentColor"}]])

(def github-icon
  [:svg {:width "24", :height "24", :viewbox "0 0 24 24", :fill "none", :xmlns "http://www.w3.org/2000/svg"}
   [:path {:fill-rule "evenodd", :clip-rule "evenodd", :d "M11.9989 0C5.37254 0 0 5.5085 0 12.3041C0 17.7401 3.43804 22.3512 8.20651 23.979C8.8069 24.0915 9.02569 23.7116 9.02569 23.3853C9.02569 23.0937 9.01538 22.3195 9.00948 21.2931C5.67163 22.0363 4.96737 19.6434 4.96737 19.6434C4.4215 18.2219 3.63473 17.8435 3.63473 17.8435C2.5452 17.0807 3.71724 17.0958 3.71724 17.0958C4.9217 17.1826 5.55523 18.3639 5.55523 18.3639C6.62562 20.2439 8.36416 19.7009 9.04779 19.3859C9.15682 18.5913 9.46622 18.049 9.80951 17.7416C7.14497 17.4311 4.34341 16.3752 4.34341 11.6605C4.34341 10.3176 4.8112 9.21936 5.57881 8.35906C5.45505 8.04787 5.04325 6.79707 5.69594 5.1029C5.69594 5.1029 6.7037 4.77207 8.99622 6.36427C9.95316 6.09085 10.9801 5.9549 12.0004 5.95036C13.0192 5.9549 14.0461 6.09085 15.0045 6.36427C17.2956 4.77207 18.3011 5.1029 18.3011 5.1029C18.956 6.79707 18.5442 8.04787 18.4205 8.35906C19.1895 9.21936 19.6544 10.3176 19.6544 11.6605C19.6544 16.3873 16.8484 17.4274 14.175 17.7317C14.606 18.1117 14.9898 18.8625 14.9898 20.0105C14.9898 21.6549 14.975 22.9819 14.975 23.3853C14.975 23.7146 15.1909 24.0975 15.8001 23.9774C20.5649 22.3467 24 17.7385 24 12.3041C24 5.5085 18.6267 0 11.9989 0Z", :fill "currentColor"}]])

(def clerk-logo
  [:img.object-scale-down
   {:src "https://cdn.nextjournal.com/data/QmSucfUyXCMKg1QbgR3QmLEWiRJ9RJvPum5GqjLPsAyngx?filename=clerk-eye.png&content-type=image/png"
    :class "max-h-[290px]"}])

(def star-icon
  [:svg {:width "24", :height "24", :viewbox "0 0 24 24", :fill "none", :xmlns "http://www.w3.org/2000/svg"}
   [:path {:fill-rule "evenodd", :clip-rule "evenodd", :d "M12 0.375C12.21 0.374864 12.4159 0.433521 12.5943 0.54433C12.7727 0.65514 12.9165 0.813678 13.0095 1.002L15.8325 6.7245L22.1475 7.6425C22.3552 7.67266 22.5503 7.76033 22.7108 7.89558C22.8713 8.03082 22.9907 8.20827 23.0556 8.40785C23.1206 8.60743 23.1284 8.8212 23.0781 9.02498C23.0279 9.22876 22.9217 9.41443 22.7715 9.561L18.2025 14.016L19.281 20.304C19.3166 20.5109 19.2935 20.7236 19.2145 20.9181C19.1355 21.1126 19.0036 21.2811 18.8338 21.4045C18.664 21.528 18.4631 21.6015 18.2537 21.6166C18.0443 21.6318 17.8348 21.5881 17.649 21.4905L12 18.5205L6.351 21.4905C6.16528 21.588 5.956 21.6317 5.74679 21.6165C5.53757 21.6014 5.33676 21.528 5.16703 21.4048C4.99729 21.2815 4.86539 21.1133 4.78622 20.919C4.70706 20.7248 4.68377 20.5123 4.719 20.3055L5.799 14.0145L1.227 9.561C1.07635 9.41448 0.969751 9.2287 0.91928 9.0247C0.868809 8.82071 0.876485 8.60665 0.941439 8.40679C1.00639 8.20693 1.12603 8.02926 1.28679 7.89392C1.44754 7.75857 1.643 7.67095 1.851 7.641L8.166 6.7245L10.9905 1.002C11.0835 0.813678 11.2273 0.65514 11.4057 0.54433C11.5841 0.433521 11.79 0.374864 12 0.375V0.375ZM12 4.0425L9.9225 8.25C9.84183 8.41335 9.72268 8.55467 9.57532 8.66179C9.42795 8.76892 9.25678 8.83865 9.0765 8.865L4.431 9.54L7.791 12.816C7.92173 12.9433 8.01954 13.1005 8.07598 13.274C8.13243 13.4475 8.14582 13.6322 8.115 13.812L7.323 18.438L11.4765 16.254C11.6379 16.1691 11.8176 16.1248 12 16.1248C12.1824 16.1248 12.3621 16.1691 12.5235 16.254L16.6785 18.438L15.8835 13.812C15.8527 13.6322 15.8661 13.4475 15.9225 13.274C15.979 13.1005 16.0768 12.9433 16.2075 12.816L19.5675 9.5415L14.9235 8.8665C14.7432 8.84015 14.5721 8.77042 14.4247 8.66329C14.2773 8.55617 14.1582 8.41485 14.0775 8.2515L12 4.041V4.0425Z", :fill "currentColor"}]])

(def stars-badge
  [:div.py-1.px-2.rounded-full.text-greenish.ml-2.flex.items-center.bg-greenish-30
   {:class "h-[22px]"}
   786])

(def emacs-icon
  [:svg {:width "42", :height "42", :viewbox "0 0 42 42", :fill "none", :xmlns "http://www.w3.org/2000/svg"}
   [:path {:d "M21 42C9.42 42 0 32.579 0 21C0 9.421 9.42 0 21 0C32.58 0 42 9.421 42 21C42 32.579 32.58 42 21 42ZM21 2C10.523 2 2 10.523 2 21C2 31.477 10.523 40 21 40C31.477 40 40 31.477 40 21C40 10.523 31.477 2 21 2Z", :fill "currentColor"}]
   [:path {:d "M31.824 29.8231C31.824 31.3151 28.218 32.8621 27.445 33.0831C25.235 33.7181 23.494 34.0091 21.228 34.0091C19.833 34.0091 19 34.0091 16 34.0091C19.319 33.5871 23.589 32.7651 26 32.0091C26.883 31.7321 26.593 31.0451 26 31.0091C22.038 30.7681 13.819 30.4641 11.254 27.9861C10.536 27.2951 10.177 26.4801 10.177 25.5821C10.177 21.7141 17.54 20.4981 20.938 20.1531C16.877 18.7721 13.299 15.7881 13.299 14.1161C13.299 12.5411 15.124 11.4231 18.426 11.4231C21.651 11.4231 26.484 11.1041 26.539 11.0901C27.658 10.8691 27.862 10.8971 27.865 10.4271C27.851 10.2201 27.326 9.90208 26.58 9.90208C26.58 9.90208 22.001 10.0081 21.001 10.0081C24.254 8.93408 26.188 7.51208 28.302 8.14708C29.504 8.50608 30.36 9.34908 30.568 10.4131C30.775 11.4491 30.609 12.2921 30.098 12.9131C29.159 14.0461 24.061 14.0141 23.001 14.0081C21.751 14.0001 25.001 14.0081 21.001 14.0081C19.27 14.0081 17.168 14.1511 17.95 15.5151C18.027 15.6491 19.001 16.6431 20.368 17.4581C22.178 18.5491 27.983 20.3321 28.052 20.3591C28.162 20.4141 28.245 20.5251 28.232 20.6631C28.218 20.7871 28.121 20.8981 27.983 20.9251C23.562 21.8091 16.669 23.9781 17.014 26.0501C17.304 27.7491 20.454 28.5501 25.883 28.3431H26.173C28.909 28.3451 31.824 28.4561 31.824 29.8231Z", :fill "currentColor"}]])

(def clojure-icon
  [:svg {:width "42", :height "43", :viewbox "0 0 42 43", :fill "none", :xmlns "http://www.w3.org/2000/svg"}
   [:path {:d "M20.1323 21.3767C19.921 21.8348 19.6952 22.3414 19.455 22.8795C18.6085 24.7866 17.6792 27.111 17.3353 28.602C17.1988 29.2307 17.1306 29.8791 17.1371 30.5222C17.1371 30.8096 17.1581 31.1102 17.1778 31.4186C18.3683 31.8557 19.6598 32.1024 21.0064 32.1024C22.1968 32.1024 23.3781 31.9095 24.5068 31.5354C24.2404 31.2953 23.9936 31.0354 23.7758 30.7624C22.2782 28.8553 21.4513 26.0676 20.1323 21.3767V21.3767ZM14.6211 11.9214C11.6614 14.0004 9.90527 17.3854 9.89739 21C9.90396 24.5543 11.6141 27.8972 14.4911 29.9828C15.175 27.1464 16.8838 24.5477 19.4471 19.3397C19.2896 18.921 19.1203 18.4643 18.9274 17.9786C18.2173 16.2028 17.191 14.1369 16.2761 13.2011C15.7892 12.6958 15.2301 12.2719 14.6211 11.9214ZM30.6716 33.7352C29.203 33.5514 27.985 33.3323 26.9271 32.9569C22.7901 35.0004 17.8905 34.7681 13.9648 32.3361C10.0418 29.9014 7.65564 25.6161 7.65564 21.0013C7.65564 17.1596 9.30939 13.503 12.2087 10.9738C11.4488 10.7888 10.6705 10.6916 9.88952 10.6916C5.97302 10.7271 1.84521 12.8927 0.120581 18.7451C-0.0369187 19.5917 0.00508128 20.2348 0.00508128 21.0013C0.00508128 32.6025 9.40389 42.0013 21.0051 42.0013C28.107 42.0013 34.3768 38.4733 38.1765 33.0724C36.1277 33.5856 34.1445 33.8323 32.4553 33.8389C31.8201 33.8389 31.2255 33.8034 30.6703 33.7352H30.6716ZM26.7276 29.6678C26.8588 29.7294 27.1528 29.8397 27.5558 29.9473C30.4131 27.8631 32.1023 24.5398 32.1088 21C32.0944 14.868 27.1318 9.90545 20.9998 9.89757C19.8173 9.89757 18.6413 10.0905 17.5138 10.4646C19.77 13.0279 20.8568 16.7068 21.9028 20.7191V20.7257C21.9094 20.7323 22.2441 21.8387 22.8111 23.3153C23.3781 24.7853 24.1918 26.6175 25.0738 27.9444C25.6553 28.8383 26.2905 29.4761 26.7276 29.6664V29.6678ZM20.9998 9.86847e-06C14.2326 -0.00655263 7.87483 3.26026 3.93733 8.77145C5.92052 7.53376 7.94308 7.08226 9.71364 7.0967C12.1536 7.10326 14.0672 7.86189 14.9899 8.38164C15.2156 8.5037 15.427 8.64807 15.6343 8.78588C19.7556 6.97332 24.5213 7.3697 28.2934 9.83063C32.0655 12.2916 34.3427 16.4968 34.3427 21.0013C34.3427 24.57 32.9147 27.9878 30.3698 30.4973C30.9722 30.5655 31.614 30.6062 32.2703 30.5983C34.596 30.5983 37.1108 30.0851 38.9903 28.5075C40.2201 27.468 41.253 25.9508 41.82 23.6814C41.9368 22.7994 41.9985 21.9122 41.9985 21.0026C41.9985 9.40145 32.5997 0.00263487 20.9985 0.00263487L20.9998 9.86847e-06Z", :fill "currentColor"}]])

(def bolt-icon
  [:svg {:width "42", :height "42", :viewbox "0 0 42 42", :fill "none", :xmlns "http://www.w3.org/2000/svg"}
   [:path {:fill-rule "evenodd", :clip-rule "evenodd", :d "M39.5 21C39.5 31.2173 31.2173 39.5 21 39.5C10.7827 39.5 2.5 31.2173 2.5 21C2.5 10.7827 10.7827 2.5 21 2.5C31.2173 2.5 39.5 10.7827 39.5 21ZM42 21C42 32.598 32.598 42 21 42C9.40202 42 0 32.598 0 21C0 9.40202 9.40202 0 21 0C32.598 0 42 9.40202 42 21ZM24 8L10 23H21L18 34L32 19H21L24 8Z", :fill "currentColor"}]])

(def eye-icon
  [:svg {:width "44", :height "34", :viewbox "0 0 44 34", :fill "none", :xmlns "http://www.w3.org/2000/svg"}
   [:path {:d "M2.10372 17.6448C1.96556 17.2303 1.96542 16.7815 2.10334 16.3669C4.88007 8.01945 12.7542 2 22.0342 2C31.31 2 39.1811 8.01385 41.9613 16.3552C42.0994 16.7697 42.0996 17.2185 41.9616 17.6332C39.1849 25.9806 31.3108 32 22.0307 32C12.755 32 4.8839 25.9862 2.10372 17.6448Z", :stroke "currentColor", :stroke-width "2.5", :stroke-linecap "round", :stroke-linejoin "round"}]
   [:path {:d "M28.0326 17C28.0326 20.3137 25.3463 23 22.0326 23C18.7189 23 16.0326 20.3137 16.0326 17C16.0326 13.6863 18.7189 11 22.0326 11C25.3463 11 28.0326 13.6863 28.0326 17Z", :stroke "currentColor", :stroke-width "2.5", :stroke-linecap "round", :stroke-linejoin "round"}]])

(def compose-icon
  [:svg {:width "43", :height "42", :viewbox "0 0 43 42", :fill "none", :xmlns "http://www.w3.org/2000/svg"}
   [:path {:fill-rule "evenodd", :clip-rule "evenodd", :d "M20 17.8142C20 18.527 19.9171 19.2205 19.7605 19.8855C15.4742 19.1965 12.2 15.4806 12.2 11C12.2 10.2872 12.2829 9.59371 12.4395 8.92871C16.7258 9.61773 20 13.3336 20 17.8142ZM21.2 0C16.6068 0 12.6712 2.81519 11.0244 6.81424L11 6.81421C4.92487 6.81421 0 11.7391 0 17.8142C0 21.2759 1.59906 24.3641 4.09883 26.3805C3.63787 27.5941 3.3855 28.9104 3.3855 30.2856C3.3855 36.3608 8.31037 41.2856 14.3855 41.2856C16.959 41.2856 19.3261 40.4019 21.1999 38.9213C23.0736 40.4019 25.4408 41.2856 28.0143 41.2856C34.0894 41.2856 39.0143 36.3608 39.0143 30.2856C39.0143 28.9104 38.7619 27.5941 38.3009 26.3805C40.8006 24.3641 42.3997 21.2759 42.3997 17.8142C42.3997 11.7391 37.4748 6.81421 31.3997 6.81421C31.3916 6.81421 31.3836 6.81422 31.3756 6.81424C29.7288 2.81519 25.7931 0 21.2 0ZM29.2741 7.01941C27.8051 4.04546 24.7414 2 21.2 2C17.6585 2 14.5948 4.04549 13.1259 7.01947C16.8035 7.73958 19.8248 10.2923 21.1998 13.6878C22.5749 10.2922 25.5963 7.73945 29.2741 7.01941ZM22.6392 19.8855C22.4825 19.2205 22.3997 18.527 22.3997 17.8142C22.3997 13.3335 25.674 9.61756 29.9605 8.92866C30.1171 9.59368 30.2 10.2872 30.2 11C30.2 15.4807 26.9256 19.1966 22.6392 19.8855ZM24.7429 26.5721C25.1589 27.7322 25.3855 28.9824 25.3855 30.2856C25.3855 33.0582 24.3598 35.5911 22.6671 37.5257C24.1621 38.6317 26.0118 39.2856 28.0143 39.2856C32.9848 39.2856 37.0143 35.2562 37.0143 30.2856C37.0143 29.3207 36.8624 28.3912 36.5813 27.5198C35.0373 28.3458 33.2732 28.8142 31.3997 28.8142C28.8978 28.8142 26.591 27.979 24.7429 26.5721ZM35.7553 25.6919C34.4646 26.407 32.9797 26.8142 31.3997 26.8142C28.1072 26.8142 25.2277 25.0463 23.6587 22.4079C24.9493 21.6928 26.4342 21.2856 28.0143 21.2856C31.3067 21.2856 34.1862 23.0536 35.7553 25.6919ZM37.3905 24.5307C35.5224 21.4935 32.2198 19.4331 28.4267 19.2932C30.7389 17.2767 32.2 14.309 32.2 11C32.2 10.2582 32.1266 9.53363 31.9866 8.83304C36.6836 9.1354 40.3997 13.0408 40.3997 17.8142C40.3997 20.4841 39.2371 22.8825 37.3905 24.5307ZM14.3855 21.2856C15.9655 21.2856 17.4504 21.6928 18.741 22.4079C17.172 25.0462 14.2925 26.8142 11 26.8142C9.42 26.8142 7.9351 26.4071 6.64448 25.692C8.21352 23.0536 11.093 21.2856 14.3855 21.2856ZM11 28.8142C9.12653 28.8142 7.36246 28.3459 5.81847 27.5198C5.53735 28.3912 5.3855 29.3207 5.3855 30.2856C5.3855 35.2562 9.41494 39.2856 14.3855 39.2856C16.3879 39.2856 18.2376 38.6317 19.7327 37.5257C18.04 35.5911 17.0143 33.0582 17.0143 30.2856C17.0143 28.9824 17.2409 27.7321 17.6569 26.5719C15.8088 27.9789 13.502 28.8142 11 28.8142ZM13.9732 19.2932C10.18 19.433 6.87736 21.4935 5.00919 24.5307C3.16261 22.8825 2 20.4841 2 17.8142C2 13.0407 5.71623 9.13523 10.4134 8.83302C10.2734 9.53362 10.2 10.2582 10.2 11C10.2 14.309 11.661 17.2767 13.9732 19.2932ZM21.1999 36.165C19.838 34.5879 19.0143 32.5329 19.0143 30.2856C19.0143 28.0383 19.838 25.9834 21.1999 24.4063C22.5618 25.9834 23.3855 28.0383 23.3855 30.2856C23.3855 32.5329 22.5618 34.5879 21.1999 36.165Z", :fill "currentColor"}]])

(def publish-icon
  [:svg {:width "46", :height "46", :viewbox "0 0 46 46", :fill "none", :xmlns "http://www.w3.org/2000/svg"}
   [:path {:d "M23 44C32.7827 44 41.003 37.3108 43.3366 28.2569M23 44C13.2173 44 4.99698 37.3108 2.66336 28.2569M23 44C28.799 44 33.5 34.598 33.5 23C33.5 11.402 28.799 2 23 2M23 44C17.201 44 12.5 34.598 12.5 23C12.5 11.402 17.201 2 23 2M23 2C30.8521 2 37.6977 6.30953 41.3005 12.6924M23 2C15.1479 2 8.30232 6.30953 4.69949 12.6924M41.3005 12.6924C36.3927 16.9342 29.996 19.5 23 19.5C16.004 19.5 9.60729 16.9342 4.69949 12.6924M41.3005 12.6924C43.0192 15.7373 44 19.2541 44 23C44 24.8153 43.7697 26.5768 43.3366 28.2569M43.3366 28.2569C37.3112 31.5977 30.3778 33.5 23 33.5C15.6222 33.5 8.68879 31.5977 2.66336 28.2569M2.66336 28.2569C2.23033 26.5768 2 24.8153 2 23C2 19.2541 2.98076 15.7373 4.69949 12.6924", :stroke "currentColor", :stroke-width "2.5", :stroke-linecap "round", :stroke-linejoin "round"}]])

(def twitter-viewer
  {:transform-fn clerk/mark-presented
   :render-fn '(fn []
                 (v/html
                  [:div {:ref (fn [el]
                                (when (and el (not (js/document.getElementById "twitter-js")))
                                  (let [twitter-js (doto (js/document.createElement "script")
                                                     (.setAttribute "src" "https://platform.twitter.com/widgets.js")
                                                     (.setAttribute "async" true)
                                                     (.setAttribute "charset" "utf-8")
                                                     (.setAttribute "id" "twitter-js"))]
                                    (.. js/document (querySelector "head") (appendChild twitter-js)))))}]))})

{::clerk/visibility {:result :show}}

(clerk/with-viewer twitter-viewer nil)

(clerk/html
 [:<>
  [:style {:type "text/css"}
   ":root {
     --greenish: rgba(146, 189, 154, 1);
     --greenish-60: rgba(146, 189, 154, 0.6);
     --greenish-50: rgba(146, 189, 154, 0.5);
     --greenish-30: rgba(146, 189, 154, 0.3)
   }
   body { background: #000; font-family: 'Inter', sans-serif; color: var(--greenish); }
   a { color: var(--greenish); transition: all 0.125s ease;}
   a:hover { color: white; }
   .viewer-notebook { padding: 0; }
   .viewer-result { margin: 0; }
   .viewer-result + .viewer-result { margin: 0; }
   .font-iosevka { font-family: 'Iosevka Web', monospace; }
   .font-inter { font-family: 'Inter', sans-serif; }
   .text-greenish { color: var(--greenish); }
   .text-greenish-60 { color: var(--greenish-60); }
   .bg-greenish { background-color: var(--greenish); }
   .bg-greenish-30 { background-color: var(--greenish-30); }
   .border-greenish-50 { border: 4px solid var(--greenish-30); }
   .separator-top { border-top: 4px solid var(--greenish-50); }
   .separator-bottom { border-bottom: 4px solid var(--greenish-50); }
   .section-heading { border-top: 4px solid var(--greenish-50); }
   .link-hairline { border-bottom: 1px solid var(--greenish-60); }
   .link-hairline:hover { border-color: white; }
   .twitter-card iframe { border: 3px solid var(--greenish-30); border-radius: 15px; overflow: hidden; margin-top: -10px;"]
  [:link {:rel "preconnect" :href "https://fonts.bunny.net"}]
  [:link {:rel "stylesheet" :href "https://fonts.bunny.net/css?family=inter:400,600"}]
  [:link {:rel "preconnect" :href "https://ntk148v.github.io"}]
  [:link {:rel "stylesheet" :href "https://ntk148v.github.io/iosevkawebfont/latest/iosevka.css"}]])

^{::clerk/width :full}
(clerk/html
 [:div.px-8.lg:px-0.lg:container.md:mx-auto.not-prose
  [:nav.separator-bottom.pt-12.pb-4.text-sm.flex.justify-between
   [:ul.flex
    [:li.mr-4 [:a {:href "#use-cases"} "Use Cases"]]
    [:li.mr-4 [:a {:href "#features"} "Features"]]
    [:li.mr-4 [:a {:href "#quotes"} "Quotes"]]
    [:li [:a {:href "#talks"} "Talks"]]]
   [:ul.flex
    [:li.mr-4
     [:a.flex.items-center {:href "#use-cases"} github-icon [:span.ml-2.hidden.md:inline "GitHub"]]]
    [:li [:a.flex.items-center {:href "#features"} star-icon [:span.ml-2.hidden.md:inline "Star on GitHub"] stars-badge]]]]
  [:div.mt-20.text-greenish.font-iosevka.flex.items-center
   [:div.lg:max-w-xl.xl:max-w-3xl
    [:h1.flex
     clerk-type
     [:div.flex.ml-2.lg:hidden {:class "h-[62px]"} clerk-logo]]
    [:h2.text-xl.md:text-3xl.font-medium.mt-8.font-iosevka
     [:a.link-hairline {:href "#"} "Moldable"]" Live Programming for Clojure"]
    [:p.text-xl.md:text-3xl.font-light.mt-6
     "Clerk takes a Clojure namespace and turns it into a notebook. Learn more in the "
     [:a.link-hairline {:href "#"} "Book of Clerk."]]]
   [:figure.flex-auto.ml-10.text-center.hidden.lg:flex.flex-col.justify-center
    clerk-logo
    [:figcaption.text-greenish-60.mt-4.text-xs.font-inter
     "Clerk logo by Jack Rusher."
     [:br]
     [:a.link-hairline.text-greenish-60 {:href "#"} "See notebook here."]]]]
  [:div.mt-10
   [:h2.section-heading.pt-4.text-sm
    [:span.font-iosevka.font-medium.uppercase.text-greenish "Features"]
    [:a.text-greenish-60.font-inter.font-normal.ml-3 {:href ""} "Learn more in the Book of Clerk"]]
   [:ul.font-medium.text-greenish.mt-10.md:grid.grid-cols-3.gap-6
    [:li
     [:div.flex.itesm-center.mb-4.text-greenish-60
      {:class "h-[43px]"} emacs-icon]
     [:span.text-xl.font-iosevka "Bring Your Own Editor"]
     [:p.mt-2.text-sm.text-greenish-60
      "Clerk lets you keep using your favorite editor and complements the Clojure REPL. The REPL is a peephole. See the full picture with Clerk."]]
    [:li.mt-10.md:mt-0
     [:div.flex.itesm-center.mb-4.text-greenish-60
      {:class "h-[43px]"} clojure-icon]
     [:span.text-xl.font-iosevka "Just Clojure Namespaces"]
     [:p.mt-2.text-sm.text-greenish-60
      "Use plain Clojure namespaces that you can easily put into version control and use as library code. For text-heavy documents, Clerk supports Markdown too."]]
    [:li.mt-10.md:mt-0
     [:div.flex.itesm-center.mb-4.text-greenish-60
      {:class "h-[43px]"} bolt-icon]
     [:span.text-xl.font-iosevka "Incremental Computation"]
     [:p.mt-2.text-sm.text-greenish-60
      "Clerk keeps the feedback loop short by only computing what's changed using a dependency graph of Clojure vars. This enables caching executions across JVM restarts and machines."]]
    [:li.mt-10.md:mt-0
     [:div.flex.itesm-center.mb-4.text-greenish-60
      {:class "h-[43px]"} eye-icon]
     [:span.text-xl.font-iosevka "Rich Built-In Viewers"]
     [:p.mt-2.text-sm.text-greenish-60
      "Supports Markdown, Grid, HTML, Hiccup, SVG, Vega, Images, Plotly, TeX, tables, you name it. Plus, Clerk doesn't break a sweat when zooming into moderately-sized datasets."]]
    [:li.mt-10.md:mt-0
     [:div.flex.itesm-center.mb-4.text-greenish-60
      {:class "h-[43px]"} compose-icon]
     [:span.text-xl.font-iosevka "Moldable Viewers"]
     [:p.mt-2.text-sm.text-greenish-60
      "Create custom viewers for your problem at hand. Clerk's viewer API is extensible via predicate functions, not only acting on types but also on values. Build stateful viewers with Reagent and dynamically import JavaScript libraries."]]
    [:li.mt-10.md:mt-0
     [:div.flex.itesm-center.mb-4.text-greenish-60
      {:class "h-[43px]"} publish-icon]
     [:span.text-xl.font-iosevka "Static Publishing"]
     [:p.mt-2.text-sm.text-greenish-60
      "Produce static HTML pages and serve them from your local file system or any static webserver. If you like it batteries-included, take a look at clerk.garden, Clerk's upcoming GitHub-based publishing platform."]]]]
  [:div.mt-20
   [:h2.section-heading.pt-4.text-sm
    [:span.font-iosevka.font-medium.uppercase.text-greenish "Use Cases"]
    [:a.text-greenish-60.font-inter.font-normal.ml-3 {:href ""} "See Clerk Demos Repository"]]
   [:p.text-xl.font-iosevka.text-greenish.mt-10.max-w-xl
    "Clerk is compatible with any Clojure and JVM library enabling these amazing use cases by composing libraries from Clojure's eco-system."]
   [:div.mt-10.grid.grid-cols-2.md:grid-cols-3.gap-4.md:gap-6.text-sm
    [:a.group {:href "#"}
     [:img {:src "https://cdn.nextjournal.com/data/QmWcxhxG6b2aMJSvkjkmBnRU2rNKCAhRB5rzMt8vvMwaHJ?filename=data-science.png&content-type=image/png"}]
     [:div.font-inter.text-greenish-60.mt-2.group-hover:text-white.transition-all
      [:p "Exploring the world in data using Vega, meta-csv and parsing Excel files with Docjure"]]]
    [:a.group {:href "#"}
     [:img {:src "https://cdn.nextjournal.com/data/QmVuFkrm4t48jLn5zDMHC3JCttx9hiHQvGkWKaMAJqgyde?filename=semantic.png&content-type=image/png"}]
     [:div.font-inter.text-greenish-60.mt-2.group-hover:text-white.transition-all "Semantic Queries against the world's knowledge in WikiData with Mundaneum"]]
    [:a.group {:href "#"}
     [:img {:src "https://cdn.nextjournal.com/data/QmZbPhh4kburooGW6JKnSoaHvQAHn51uQ3W5V2QZgoYQhi?filename=double-pendulum.png&content-type=image/png"}]
     [:div.font-inter.text-greenish-60.mt-2.group-hover:text-white.transition-all "Simulating physical systems with SICMUtils"]]
    [:a.group {:href "#"}
     [:img {:src "https://cdn.nextjournal.com/data/Qmb9vXz1MqewaRu3SDmx2a9ge5YzhCJ18JtzMTmgR26QnP?filename=rule-30.png&content-type=image/png"}]
     [:div.font-inter.text-greenish-60.mt-2.group-hover:text-white.transition-all "Playing with cellular automata and Clerk's moldable viewers API"]]
    [:a.group {:href "#"}
     [:img {:src "https://cdn.nextjournal.com/data/QmTUbLE3QP37iKAxBx7CSjGuUu4CDHvjU8Gnk6QNpPCh2u?filename=docs.png&content-type=image/png"}]
     [:div.font-inter.text-greenish-60.mt-2.group-hover:text-white.transition-all "Making inside-out's library documentation interactive with custom CLJS macros"]]
    [:a.group {:href "#"}
     [:img {:src "https://cdn.nextjournal.com/data/QmZHUCVDFnixs4vr4pxqnCuMjJhckZRm1mcYT9r3zY27qa?filename=spiro.png&content-type=image/png"}]
     [:div.font-inter.text-greenish-60.mt-2.group-hover:text-white.transition-all "Controlling a spirograph animation using Open Sound Control and your iPhone"]]]
   [:div.mt-20
    [:h2.section-heading.pt-4.text-sm
     [:span.font-iosevka.font-medium.uppercase.text-greenish "Quotes"]
     [:a.text-greenish-60.font-inter.font-normal.ml-3 {:href ""} "See more on Twitter"]]
    [:div.grid.md:grid-cols-2.xl:grid-cols-4.gap-6.mt-10
     [:div.twitter-card
      [:blockquote {:class "twitter-tweet", :data-theme "dark"}
       [:p {:lang "en", :dir "ltr"} "Huge shoutout to " 
        [:a {:href "https://twitter.com/usenextjournal?ref_src=twsrc%5Etfw"} "@usenextjournal"]" for " 
        [:a {:href "https://t.co/GAeLVdlk0F"} "https://t.co/GAeLVdlk0F"]", which is making the training of junior " 
        [:a {:href "https://twitter.com/hashtag/Clojure?src=hash&ref_src=twsrc%5Etfw"} "#Clojure"]" programmers a massive pleasure!"]"— Robert Stuttaford (@RobStuttaford) " 
       [:a {:href "https://twitter.com/RobStuttaford/status/1574328589306281987?ref_src=twsrc%5Etfw"} "September 26, 2022"]]]
     [:div.twitter-card
      [:blockquote {:class "twitter-tweet", :data-theme "dark"}
       [:p {:lang "en", :dir "ltr"} "Here&#39;s a fun " 
        [:a {:href "https://twitter.com/hashtag/clojure?src=hash&ref_src=twsrc%5Etfw"} "#clojure"]" notebook with " 
        [:a {:href "https://twitter.com/usenextjournal?ref_src=twsrc%5Etfw"} "@usenextjournal"]"&#39;s Clerk. Reactive UI, and changes you make in the browser save back to the file. I think there&#39;s some fun potential with an approach like this. My next steps will be to make the connection more reliable :) " 
        [:a {:href "https://t.co/XAwWjCyht6"} "pic.twitter.com/XAwWjCyht6"]]"— adam-james (@RustyVermeer) " 
       [:a {:href "https://twitter.com/RustyVermeer/status/1544901494675099649?ref_src=twsrc%5Etfw"} "July 7, 2022"]]]
     [:div.twitter-card
      [:blockquote {:class "twitter-tweet", :data-conversation "none", :data-theme "dark"}
       [:p {:lang "en", :dir "ltr"} "- this is what " 
        [:a {:href "https://twitter.com/girba?ref_src=twsrc%5Etfw"} "@girba"]" calls &quot;moldable development&quot; — thanks to work of " 
        [:a {:href "https://twitter.com/mkvlr?ref_src=twsrc%5Etfw"} "@mkvlr"]
        [:a {:href "https://twitter.com/jackrusher?ref_src=twsrc%5Etfw"} "@jackrusher"]" on Clerk, we can get similar dynamics of development as GT/Smalltalk" 
        [:br]
        [:br]"I present this to " 
        [:a {:href "https://twitter.com/girba?ref_src=twsrc%5Etfw"} "@girba"]" and " 
        [:a {:href "https://twitter.com/ericnormand?ref_src=twsrc%5Etfw"} "@ericnormand"]" here: " 
        [:a {:href "https://t.co/emrIV3VEas"} "https://t.co/emrIV3VEas"]]"— Gene Kim (@RealGeneKim) " 
       [:a {:href "https://twitter.com/RealGeneKim/status/1520852518997090305?ref_src=twsrc%5Etfw"} "May 1, 2022"]]]
     [:div.twitter-card
      [:blockquote {:class "twitter-tweet", :data-theme "dark"}
       [:p {:lang "en", :dir "ltr"} "Really impressed with how " 
        [:a {:href "https://twitter.com/usenextjournal?ref_src=twsrc%5Etfw"} "@usenextjournal"]" progressed from being &quot;an alternative&quot; to being at the forefront of notebooks and by far the most Clojurey of them all. We really need work like that in the Clojure data ecosystem. " 
        [:a {:href "https://t.co/hNf9vygq8S"} "https://t.co/hNf9vygq8S"]]"— Simon Belak (@sbelak) " 
       [:a {:href "https://twitter.com/sbelak/status/1409546462384603136?ref_src=twsrc%5Etfw"} "June 28, 2021"]]]]]
   [:div.mt-20
    [:h2.section-heading.pt-4.text-sm
     [:span.font-iosevka.font-medium.uppercase.text-greenish "Talks"]]
    [:div.mt-10.grid.md:grid-cols-2.gap-6
     [:div.border-greenish-50
      [:iframe {:width "100%", :height "315", :src "https://www.youtube.com/embed/3ANS2NTNgig", :title "YouTube video player", :frameborder "0", :allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture", :allowfullscreen true}]]
     [:div.border-greenish-50
      [:iframe {:width "100%", :height "315", :src "https://www.youtube.com/embed/8Ab3ArE8W3s", :title "YouTube video player", :frameborder "0", :allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture", :allowfullscreen true}]]
     [:div.border-greenish-50
      [:iframe {:width "100%", :height "315", :src "https://www.youtube.com/embed/Gnrh7XOt_84", :title "YouTube video player", :frameborder "0", :allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture", :allowfullscreen true}]]
     [:div.border-greenish-50
      [:iframe {:width "100%", :height "315", :src "https://www.youtube.com/embed/kp-4WuyDGww", :title "YouTube video player", :frameborder "0", :allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture", :allowfullscreen true}]]]]
   [:footer.text-sm.my-20.text-greenish-60.separator-top.pt-4
    [:ul.flex
     [:li.mr-4 "This website is built with Clerk. " [:a.link-hairline {:href "#"} "See notebook."]]]]]])
