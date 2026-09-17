# Ícone do instalador (opcional)

Coloque aqui um arquivo `app.ico` (256×256, com camadas 16/32/48/256) para
aplicar automaticamente:

- ícone do EXE gerado pelo `jpackage` (`--icon`)
- ícone do setup/atalhos do Inno Setup (`SetupIconFile`)

Enquanto o arquivo não existir, ambos os instaladores usam o ícone padrão
do Java/jpackage — nada quebra.

Como gerar um `.ico`:

1. Exporte um PNG 1024×1024 do logo do app.
2. Converta (exemplos):
   - https://convertio.co/png-ico/ ou IcoFX / GIMP, ou
   - PowerShell + ImageMagick: `magick logo.png -define icon:auto-resize=256,128,64,48,32,16 app.ico`
3. Salve como `packaging\windows\app.ico` e rode novamente o empacotamento.
