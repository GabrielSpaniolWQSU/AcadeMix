package br.edu.faculdade.service;

import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class GoogleDriveTokenStore {
    private final Path raiz;
    public GoogleDriveTokenStore(){this(Path.of("data","google-drive"));}
    public GoogleDriveTokenStore(Path raiz){this.raiz=raiz;}
    public synchronized boolean existe(long usuario){return Files.isRegularFile(raiz.resolve(usuario+".token"));}

    public synchronized void salvar(long usuario,String contexto,String token){
        try {
            Files.createDirectories(raiz);
            var dados=proteger(token.getBytes(StandardCharsets.UTF_8),contexto,true);
            var destino=raiz.resolve(usuario+".token");
            var temporario=raiz.resolve(usuario+".tmp");
            Files.write(temporario,dados);
            Files.move(temporario,destino,StandardCopyOption.REPLACE_EXISTING);
        } catch(IOException e){throw new IllegalStateException("Não foi possível salvar a conexão do Google Drive.",e);}
    }
    public synchronized String carregar(long usuario,String contexto){
        var arquivo=raiz.resolve(usuario+".token");
        if(!Files.exists(arquivo))return null;
        try {
            return new String(proteger(Files.readAllBytes(arquivo),contexto,false),StandardCharsets.UTF_8);
        } catch(IOException e){throw new IllegalStateException("Não foi possível recuperar a conexão. Conecte o Google Drive novamente.",e);}
    }
    public synchronized void remover(long usuario){
        try{Files.deleteIfExists(raiz.resolve(usuario+".token"));}
        catch(IOException e){throw new IllegalStateException("Não foi possível remover a conexão salva.",e);}
    }
    private byte[] proteger(byte[] valor,String contexto,boolean salvar) throws IOException {
        String operacao=salvar?"Protect":"Unprotect";
        String script="Add-Type -AssemblyName System.Security; "
                +"$valor=[Convert]::FromBase64String([Console]::ReadLine()); "
                +"$contexto=[Convert]::FromBase64String([Console]::ReadLine()); "
                +"try { $resultado=[Security.Cryptography.ProtectedData]::"+operacao
                +"($valor,$contexto,[Security.Cryptography.DataProtectionScope]::CurrentUser); "
                +"[Console]::Write([Convert]::ToBase64String($resultado)) } catch { exit 1 }";
        var processo=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",script)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            try(var entrada=processo.outputWriter(StandardCharsets.UTF_8)){
                entrada.write(Base64.getEncoder().encodeToString(valor));entrada.newLine();
                entrada.write(Base64.getEncoder().encodeToString(contexto.getBytes(StandardCharsets.UTF_8)));entrada.newLine();
            }
            if(!processo.waitFor(15,TimeUnit.SECONDS))throw new IOException("Tempo de proteção excedido.");
            if(processo.exitValue()!=0)throw new IOException("Falha na proteção do Windows.");
            return Base64.getDecoder().decode(new String(processo.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim());
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Operação interrompida.",e);}
        finally{if(processo.isAlive())processo.destroyForcibly();}
    }
}
