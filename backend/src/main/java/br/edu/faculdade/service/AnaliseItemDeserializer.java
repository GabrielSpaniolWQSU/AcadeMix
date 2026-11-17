package br.edu.faculdade.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.ArrayList;

/** Converte os detalhes estruturados da IA em texto para a lista de análise. */
public class AnaliseItemDeserializer extends JsonDeserializer<String> {
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode item=parser.readValueAsTree();
        String texto=texto(item,0);
        if(texto.isBlank()){
            throw JsonMappingException.from(parser,"O item de análise não contém texto.");
        }
        return texto;
    }
    private String texto(JsonNode item,int profundidade){
        if(profundidade>8||item==null||item.isNull())return "";
        if(item.isTextual())return item.asText();
        var partes=new ArrayList<String>();
        if(item.isObject()){
            item.fields().forEachRemaining(campo->{
                String valor=texto(campo.getValue(),profundidade+1);
                if(!valor.isBlank())partes.add(campo.getKey()+": "+valor);
            });
        }else if(item.isArray()){
            item.forEach(valor->{
                String detalhe=texto(valor,profundidade+1);
                if(!detalhe.isBlank())partes.add(detalhe);
            });
        }
        return String.join("; ",partes);
    }
}
