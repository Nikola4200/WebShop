package com.angularspringbootecommerce.backend.services;

import com.angularspringbootecommerce.backend.dtos.*;
import com.angularspringbootecommerce.backend.exceptions.AppException;
import com.angularspringbootecommerce.backend.models.*;
import com.angularspringbootecommerce.backend.repository.*;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    @Autowired
    private final MailService mailService;

    public List<OrderDto> getOrdersByUserId(Long userId, Authentication authentication) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found.", HttpStatus.NOT_FOUND));

        if (authentication == null || !user.getEmail().equals(authentication.getName())) {
            throw new AppException("Access denied.", HttpStatus.BAD_REQUEST);
        }

        List<Order> orders = orderRepository.findAllByUserId(userId);
        List<OrderDto> orderDtos = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (Order order : orders) {
            OrderDto orderDto = new OrderDto();
            orderDto.setId(order.getId());
            orderDto.setTotal(order.getTotal());
            String dateCreatedStr = dateFormat.format(order.getDateCreated());
            orderDto.setDateCreated(dateCreatedStr);
            orderDtos.add(orderDto);
        }
        return orderDtos;
    }

    public Order createOrderFromCart(CartDto cart, Long userId, Authentication authentication) throws MessagingException {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
        String currentDateTime = dateFormatter.format(new Date());

        if (authentication == null || !user.getEmail().equals(authentication.getName())) {
            throw new AppException("Access denied.", HttpStatus.BAD_REQUEST);
        }

        Order order = new Order();
        order.setUser(user);
        order.setTotal(cart.getTotalPrice());
        order.setDateCreated(new Date());
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItemDto cartItem : cart.getCartItems()) {
            OrderItem orderItem = new OrderItem();
            Product product = productRepository.findById(cartItem.getProductId()).orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItems.add(orderItem);
        }
        order.setOrderItems(orderItems);

        String fileName = generatePdf(order);

        mailService.sendEmailWithAttachment(user.getEmail(), "Order Confirmation", "Thank you for your order!", "C:\\pdf\\" + fileName);

        return orderRepository.save(order);
    }

    public String generatePdf(Order order) {
        try {
            Document document = new Document(PageSize.A4);

            DateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String currentDateTime = dateTimeFormatter.format(new Date());

            String fileName = "User_" + order.getUser().getUsername() + "_" + currentDateTime + ".pdf";
            String filePath = "C:\\pdf\\" + fileName;

            System.out.println(filePath);

            PdfWriter.getInstance(document, new FileOutputStream(filePath));

            document.open();

            Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
            fontTitle.setSize(18);

            Paragraph paragraph = new Paragraph("Order Information", fontTitle);
            paragraph.setAlignment(Paragraph.ALIGN_CENTER);
            document.add(paragraph);

            Paragraph userNameParagraph = new Paragraph("Ordered by user: " + order.getUser().getUsername());
            userNameParagraph.setAlignment(Paragraph.ALIGN_LEFT);
            document.add(userNameParagraph);

            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(3);
            table.addCell("Product Name");
            table.addCell("Price");
            table.addCell("Quantity");

            for (OrderItem item : order.getOrderItems()) {
                table.addCell(item.getProduct().getName());
                table.addCell(item.getProduct().getPrice().toString());
                table.addCell(String.valueOf(item.getQuantity()));
            }

            document.add(table);

            document.add(new Paragraph(" "));

            Paragraph totalPriceParagraph = new Paragraph("Total Price: " + order.getTotal());
            totalPriceParagraph.setAlignment(Paragraph.ALIGN_LEFT);
            document.add(totalPriceParagraph);

            Paragraph centeredText = new Paragraph("LaptopShop.com");
            centeredText.setAlignment(Paragraph.ALIGN_CENTER);
            document.add(centeredText);

            document.close();

            System.out.println("Generated successfully");
            return fileName;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}