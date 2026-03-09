package models;

public class Order {
    private int id;
    private String status;

    public Order(){
    }

    public void setId(int id){
        this.id = id;
    }

    public int getId(){
        return this.id;
    }

    public void setStatus(String status){
        this.status = status;
    }

    public String getStatus(){
        return this.status;
    }
}
