import { HttpClient } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AnexosService {
  mensagem='';
  constructor(private http:HttpClient){}
  private async enviarServidor(id:number,arquivo:File,usuario=JSON.parse(localStorage.getItem('sessao')||'null')?.id){
    const dados=new FormData();
    dados.append('arquivo',arquivo,arquivo.name);
    await firstValueFrom(this.http.put('http://localhost:8080/api/atas-atividades/anexos/'+id,dados,{withCredentials:true,headers:{'X-Usuario-Id':String(usuario)}}).pipe(timeout(120000)));
    this.mensagem='Anexo salvo no servidor. O envio ao Drive será feito automaticamente.';
  }
  async sincronizarLocais(){
    const usuario=JSON.parse(localStorage.getItem('sessao')||'null')?.id;
    const db=await this.abrir();
    const arquivos=await new Promise<{id:number;arquivo:File}[]>((resolve,reject)=>{
      const encontrados:{id:number;arquivo:File}[]=[];
      const request=db.transaction('atas','readonly').objectStore('atas').openCursor();
      request.onerror=()=>reject(request.error);
      request.onsuccess=()=>{
        const cursor=request.result;
        if(!cursor){resolve(encontrados);return}
        encontrados.push({id:Number(cursor.key),arquivo:cursor.value});
        cursor.continue();
      };
    }).finally(()=>db.close());
    for(const item of arquivos){
      if(JSON.parse(localStorage.getItem('sessao')||'null')?.id!==usuario)return;
      await this.enviarServidor(item.id,item.arquivo,usuario);
    }
  }
  private abrir(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open('gestao-academica-anexos-conta-' + JSON.parse(localStorage.getItem('sessao')||'null')?.id, 1);
      request.onupgradeneeded = () => request.result.createObjectStore('atas');
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  async salvar(id: number, arquivo: File): Promise<void> {
    const usuario=JSON.parse(localStorage.getItem('sessao')||'null')?.id;
    const db = await this.abrir();
    try {
      await new Promise<void>((resolve, reject) => {
        const tx = db.transaction('atas', 'readwrite');
        tx.objectStore('atas').put(arquivo, id);
        tx.oncomplete = () => resolve();
        tx.onabort = () => reject(tx.error);
        tx.onerror = () => reject(tx.error);
      });
    } finally { db.close(); }
    try{await this.enviarServidor(id,arquivo,usuario)}
    catch{this.mensagem='Anexo preservado neste navegador. O envio ao servidor está pendente; abra esta aba novamente para tentar de novo.'}
  }

  async obter(id: number): Promise<File | undefined> {
    const db = await this.abrir();
    try {
      const local = await new Promise<File | undefined>((resolve, reject) => {
        const tx = db.transaction('atas', 'readonly');
        const request = tx.objectStore('atas').get(id);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
      });
      if(local)return local;
      try{
        const resposta=await firstValueFrom(this.http.get('http://localhost:8080/api/atas-atividades/anexos/'+id,{observe:'response',responseType:'blob',withCredentials:true}));
        if(!resposta.body)return undefined;
        const nome=decodeURIComponent((resposta.headers.get('X-Arquivo-Nome')||'documento.docx').replace(/\+/g,' '));
        return new File([resposta.body],nome,{type:resposta.body.type});
      }catch(erro:any){if(erro.status===404)return undefined;throw erro}
    } finally { db.close(); }
  }
}
