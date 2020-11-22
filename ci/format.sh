#!/bin/bash -e
make format

ok=yes
git diff --color=always --exit-code || ok=no

if [[ "${ok}" = "no" ]]; then
  echo -e "\e[1mLooks like some of your code isn't formatted correctly.\e[0m"
  echo "please review the changes requested above and manually preform the necessary corrections."
  echo "alternatively, install clang-format (apt install clang-format) and run make format"
  exit 1
fi