for RES in 16 20 24 32 40 48 60 64 72 80 96 128 256 512;do
  echo cp build/generated-resources/icons/icons/app/$RES.png $HOME/.local/share/icons/hicolor/$((RES))x$((RES))/apps/threadwork.png
  cp build/generated-resources/icons/icons/app/$RES.png $HOME/.local/share/icons/hicolor/$((RES))x$((RES))/apps/threadwork.png
done